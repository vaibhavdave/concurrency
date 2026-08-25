← [13. Deadlock, Livelock & Starvation](13-deadlock-livelock-starvation.md) | **14. Virtual Threads & Structured Concurrency** | Next → [15. Reactive (Project Reactor)](15-reactive-webflux.md)

# 14 — Virtual Threads and Structured Concurrency

## The failure, first

10,000 blocking I/O tasks (each just a 50ms sleep, standing in for a
network call) submitted to a fixed pool of 200 platform threads take about
2.5 seconds — because only 200 can be "in flight" at once; the other 9,800
queue up waiting their turn, each one's OS thread otherwise sitting
completely idle for 50ms doing nothing but existing. Submit the exact same
10,000 tasks with **one virtual thread per task** instead, and the whole
batch finishes in roughly 118ms — over 20x faster, doing the identical
work. Nothing about the *sleep* got faster. What changed is that virtual
threads let all 10,000 blocking waits happen **concurrently**, using only a
handful of real OS threads underneath, because blocking no longer occupies
an OS thread for the duration of the wait.

`VirtualThreadsThroughputDemo` and `PlatformThreadsThroughputDemo` measure
exactly this gap, and it's the entire reason virtual threads exist: module
01 established that OS threads are expensive and module 08 built pools to
reuse a bounded number of them — virtual threads instead make the *thread*
itself so cheap that you stop pooling them altogether and just create one
per task, the way you'd create one object per request.

## Mental model: your task gets off the highway when it's not actually driving

A **platform thread** is a 1:1 wrapper around a real OS thread — heavy to
create (roughly a megabyte of stack), and scheduled by the OS kernel. A
**virtual thread** is a lightweight, JVM-managed thread — cheap to create
(a few hundred bytes) — and many of them are multiplexed onto a small pool
of real **carrier** platform threads. The trick that makes this work: when
a virtual thread blocks on something the JVM understands (`Thread.sleep`,
blocking I/O, `java.util.concurrent` locks), it doesn't sit on its carrier
doing nothing — the JVM **unmounts** it, freeing the carrier to run a
*different* virtual thread, and remounts the original one (possibly on a
different carrier) once its blocking operation actually completes.

```
 Carrier thread (OS thread)
 ┌─────────────────────────────────────────────────────────────┐
 │  VT-1 runs  →  VT-1 blocks on I/O  →  (unmounted, parked)    │
 │                        │                                     │
 │                        ▼                                     │
 │  VT-2 mounts and runs  →  VT-2 blocks  → (unmounted)         │
 │                        │                                     │
 │                        ▼                                     │
 │  VT-1's I/O completes → VT-1 remounts (maybe on another      │
 │  carrier) and continues                                      │
 └─────────────────────────────────────────────────────────────┘
```

Picture a small number of toll-booth lanes (carriers) serving an enormous
number of cars (virtual threads): a car that's stopped to refuel (blocked
on I/O) pulls off to the side instead of occupying a lane — the lane stays
free for a car that's actually moving. That's the entire mechanism behind
the 20x speedup above: at any instant, only virtual threads doing *actual*
CPU work occupy a carrier; the other 9,800, mid-sleep, cost nothing but a
small amount of heap memory.

## Concept, from first principles

### Virtual threads add cheap concurrency, not more parallelism

This is the distinction the whole module rests on: **parallelism** is bound
by the number of CPU cores — virtual threads don't add cores, so CPU-bound
work gets no speedup from switching to them, since all the actual compute
still has to happen on the same fixed number of cores. What virtual threads
add is cheap **concurrency** for *blocking* work — the ability to have tens
of thousands of tasks simultaneously "waiting" for I/O without each one
tying up a full OS thread to do so. Applying virtual threads to a CPU-bound
workload (image processing, sorting, hashing) buys nothing, because there's
no blocking to unmount during — every virtual thread doing real
computation still needs a carrier the entire time it's actually computing.

### Pinning: the one place a virtual thread cannot unmount

`VirtualThreadPinningDemo` runs the same 50-task, blocking-while-holding-a-
guard workload two ways:

```java
// synchronized: PINS the carrier while blocked
synchronized (MONITOR) {
    Thread.sleep(HOLD_MILLIS);   // carrier cannot be freed during this sleep
}

// ReentrantLock: does NOT pin the carrier while blocked
LOCK.lock();
try {
    Thread.sleep(HOLD_MILLIS);   // carrier IS freed during this sleep
} finally {
    LOCK.unlock();
}
```

If a virtual thread blocks while holding a monitor entered via
`synchronized`, the JVM **cannot** safely unmount it — the current monitor
implementation doesn't support releasing and reacquiring monitor state
across an unmount/remount. The carrier stays **pinned**, occupied for the
entire blocking call, exactly as if it were a platform thread. `
java.util.concurrent.locks.ReentrantLock` (and the rest of `j.u.c.locks`,
module 05) is implemented specifically to cooperate with the virtual thread
scheduler — blocking while holding one still unmounts normally, freeing the
carrier for other virtual threads. Since the default carrier pool is sized
to the number of CPU cores (small, by design), pinning on a hot path is a
real capacity hazard: enough pinned virtual threads can starve the entire
application's ability to make progress on anything else, exactly the way a
handful of stalled cars blocking every toll lane would back up the whole
highway. This is precisely why the standard advice for virtual-thread-heavy
code is: replace `synchronized` with `ReentrantLock` on any hot path a
virtual thread might block within.

### Structured concurrency: no subtask outlives its scope

Fork several subtasks and it's easy to lose track of one — a leaked thread
still running after its caller has returned, or a caller that gets a result
without knowing a sibling task silently failed. Structured concurrency is
the discipline of tying every forked subtask's lifetime to a single lexical
scope: when the scope exits, every subtask has definitively succeeded,
failed, or been cancelled — none can outlive the block that spawned it.
`ManualStructuredConcurrencyDemo` approximates this with a
try-with-resources `ExecutorService` (which implements `AutoCloseable` as
of Java 19):

```java
try (ExecutorService scope = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<T>> futures = new ArrayList<>();
    for (int delay : taskDelaysMillis) {
        futures.add(scope.submit(() -> work.apply(delay)));
    }
    List<T> results = new ArrayList<>();
    for (Future<T> future : futures) {
        try {
            results.add(future.get());
        } catch (ExecutionException e) {
            futures.forEach(f -> f.cancel(true));  // cancel siblings on first failure
            throw e;
        }
    }
    return results;
} // close() waits for remaining tasks -- no subtask can leak past this point
```

`close()` on the executor guarantees no subtask leaks past the
try-with-resources block — that part is genuinely structural. But the
sibling-cancellation behavior here is only as good as the discipline
writing it: cancellation happens only once the loop's `future.get()`
*happens to observe* a failure, which means any sibling whose `future.get()`
was called *before* the failing one is discovered runs to completion
regardless, and — depending on ordering — some siblings might not be
cancelled until well after the failure actually occurred.

The real preview API, **`StructuredTaskScope`** (JEP 480), closes exactly
that gap — it isn't compiled into this repo (it would require
`--enable-preview` for the whole build), but the intended usage is worth
knowing verbatim:

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> user = scope.fork(() -> fetchUser(userId));
    Subtask<Integer> orderCount = scope.fork(() -> fetchOrderCount(userId));

    scope.join();           // wait for both, or until one fails
    scope.throwIfFailed();  // propagate the first failure; others already cancelled

    return new UserSummary(user.get(), orderCount.get());
} // scope.close() guarantees no subtask leaks past this point
```

`ShutdownOnFailure` cancels every sibling **the instant** any one subtask
fails — not "whenever the caller's loop happens to call `get()` and notice"
— and alternative join policies like `ShutdownOnSuccess` express
race-style "first success wins" semantics natively. The manual
`ExecutorService` version is a reasonable approximation for pre-preview
JDKs; `StructuredTaskScope` makes the "no subtask outlives its scope"
invariant a structural property of the API instead of a matter of writing
the cancellation loop correctly every time.

## Misconceptions worth naming directly

- **Belief: "Switching a CPU-bound workload to virtual threads will speed
  it up, the same way it sped up the I/O-bound demo."**
  Wrong — virtual threads add cheap concurrency for *blocking* work, not
  additional parallelism; CPU-bound tasks still compete for the same fixed
  number of cores regardless of how many virtual threads wrap them.

- **Belief: "Pooling virtual threads (e.g., a fixed-size pool of 200
  virtual threads) is a sensible way to bound resource usage, the way it
  is for platform threads."**
  Wrong — pooling defeats the entire point; virtual threads are meant to
  be created per task (cheaply, by the thousands), not reused, since
  reuse-to-avoid-creation-cost is a platform-thread concern that virtual
  threads were specifically designed to make unnecessary.

- **Belief: "As long as I'm using virtual threads, blocking while holding
  any kind of lock is fine — that's the whole point of virtual threads."**
  Wrong — blocking while holding a monitor entered via `synchronized`
  pins the carrier for the duration, exactly like a platform thread would
  be blocked; only `j.u.c` locks (`ReentrantLock` and friends) cooperate
  with the scheduler to unmount normally.

- **Belief: "The manual `try-with-resources` `ExecutorService` pattern
  gives the same guarantees as `StructuredTaskScope`."**
  Wrong — it guarantees no subtask leaks past the block (via `close()`),
  but sibling cancellation on failure is only as prompt and correct as the
  hand-written loop that implements it — it can lag behind when a failure
  actually occurred, unlike `ShutdownOnFailure`'s immediate cancellation.

## Where this shows up for real

Virtual threads are the reason Spring Boot added
`spring.threads.virtual.enabled=true` — flipping that switch moves the
embedded Tomcat request-handling threads from platform threads to virtual
threads, letting a server handle vastly more concurrent, blocking (JDBC,
downstream HTTP call) requests without needing a correspondingly enormous
platform thread pool. The pinning hazard is a documented, real operational
concern for exactly this deployment shape: legacy code paths that still use
`synchronized` on a request-handling hot path can silently throttle an
otherwise virtual-thread-friendly server under load. Structured concurrency
is the direct ancestor of "fan out to several backend calls, fail fast if
any fails, never leak a forgotten in-flight request" — exactly the shape
module 10's `CompletableFuture.allOf` addresses too, but with an explicit,
scoped lifetime guarantee `CompletableFuture` alone doesn't provide.

## Check yourself

1. Why did switching 10,000 blocking tasks from a 200-thread platform pool
   to one virtual thread each produce a roughly 20x speedup, when the
   underlying sleep duration didn't change at all?
2. Under what kind of workload would switching to virtual threads provide
   little or no benefit, and why?
3. Why does blocking inside a `synchronized` block pin a virtual thread's
   carrier, while blocking while holding a `ReentrantLock` does not?
4. In the manual `try-with-resources`-`ExecutorService` approximation of
   structured concurrency, what specifically is *not* guaranteed compared
   to the real `StructuredTaskScope.ShutdownOnFailure`?
5. Why is pooling virtual threads (reusing a fixed set of them across
   tasks) considered a misuse rather than a reasonable optimization?

---

<details>
<summary>Answers</summary>

1. Because a platform thread's blocking sleep occupies a full OS thread
   for the entire 50ms, so only 200 tasks can be "in flight" at once and
   the rest queue up serially behind them. A virtual thread's sleep
   unmounts it from its carrier for the duration of the block, freeing the
   carrier to run other virtual threads — so all 10,000 sleeps can overlap
   concurrently using only a handful of real OS threads, instead of
   queuing behind a fixed pool of 200.
2. CPU-bound workloads — since virtual threads add cheap concurrency for
   blocking operations, not additional parallelism. A task that's
   continuously computing (never blocking) still needs a carrier for its
   entire duration, so wrapping it in a virtual thread doesn't relieve the
   same fixed-core-count bottleneck that platform threads face.
3. Because releasing and reacquiring monitor state (the mechanism behind
   `synchronized`) across an unmount/remount isn't supported by the
   current JVM monitor implementation, so the JVM has no safe way to
   unmount a virtual thread that's blocked while holding one — the carrier
   stays occupied for the duration. `ReentrantLock` and other `j.u.c` locks
   are implemented to cooperate with the virtual-thread scheduler, so
   blocking while holding one still allows a normal unmount.
4. Sibling subtasks are only cancelled once the hand-written loop's
   `future.get()` happens to encounter a failure — which can lag behind
   the actual moment a sibling failed, and any subtask whose `get()` was
   already called before that point runs to completion regardless.
   `StructuredTaskScope.ShutdownOnFailure` cancels every sibling
   immediately when any one subtask fails, with no such lag.
5. Because virtual threads are designed to be cheap enough to create fresh,
   per task, by the thousands — pooling exists specifically to amortize
   the *expensive* creation cost of platform threads, a cost virtual
   threads don't have. Pooling them adds complexity and reuse-related
   hazards (e.g., leaked state between tasks) with no corresponding benefit.

</details>

---

← [13. Deadlock, Livelock & Starvation](13-deadlock-livelock-starvation.md) | Next → [15. Reactive (Project Reactor)](15-reactive-webflux.md)
