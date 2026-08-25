← [Index](README.md) | **01. Thread Fundamentals** | Next → [02. Race Conditions & `synchronized`](02-race-conditions-and-synchronized.md)

# 01 — Thread Fundamentals

## The failure, first

Say you spawn 200 threads to speed up a batch of independent, CPU-bound work
that used to run on one thread. You measure it. It's *slower*. Nobody told
you that was possible — more workers should mean more work done per second,
not less. Something about how threads actually run is not what the word
"parallel" suggested.

That something is the gap between *concurrency* (many threads exist and are
eligible to make progress) and *parallelism* (many threads are actually
executing at the same instant, on separate cores). A thread is not a free
lane on an infinite highway — it's a claim on a scarce resource (a core, a
kernel scheduling slot, an OS-level stack), and every thread beyond the
number of cores you have is competing, not helping. `ContextSwitchCostDemo`
in this module makes that concrete: it does the exact same total number of
increments with 1, 2, 4, 8, and 50 threads, and the more-threads runs get
*slower*, not faster, because the OS spends real time saving and restoring
register state and stacks every time it swaps which thread a core is
running — time spent context-switching instead of computing.

Everything else in this module is really in service of one question: if
threads are this expensive and this easy to misuse, what exactly do you
have control over, and what do you actually know about a thread's state at
any moment?

## Mental model: a thread is a worker with its own private notebook

Picture each thread as a person with their own call stack (a notebook where
they jot down "which function am I in, and what were my local variables") and
their own program counter (which line they're currently on). The `Thread`
object in Java is a thin handle to that worker — creating a `Thread` hires
someone; calling `start()` tells them to begin working; calling `join()`
means "wait here until that person is done." Everything downstream in this
curriculum — races, visibility, locks — is about what happens when several
of these independent workers touch the *same* shared notebook (heap memory)
instead of their own private one.

## Concept, from first principles

### Four ways to hand a worker their job

A `Thread` needs to know what code to run. The repo's `ThreadCreationDemo`
shows all four idioms you'll ever see in real code, and they're equivalent
in effect — differing only in *how* the work gets attached:

- **Subclass `Thread`** and override `run()`. Rare in real code (it forces
  the work to *be-a* Thread, when it usually just *needs-a* thread to run
  on) but the historical starting point.
- **Pass a `Runnable`** to `Thread`'s constructor — the work is a separate
  object, decoupled from "thread-ness." This is the idiomatic form.
- **Pass a lambda** — a `Runnable` is a single-abstract-method interface, so
  a lambda is just sugar over the same mechanism.
- **`Callable` + `FutureTask`** — the only one of the four that can *return
  a value* or throw a checked exception back to the caller. `FutureTask`
  wraps a `Callable` and itself implements `Runnable`, so it can still be
  handed to a `Thread`; `futureTask.get()` blocks until the result exists.
  This is the seed of everything in module 10 (`Future`/`CompletableFuture`)
  — a `FutureTask` is a `Future` you can also run directly on a `Thread`.

### The lifecycle is a state machine, not a mood

`Thread.getState()` returns one of exactly six values, and each is a
*precise, checkable claim* about what the JVM is doing with that thread —
not a vague description:

```
NEW → RUNNABLE → (BLOCKED | WAITING | TIMED_WAITING) → RUNNABLE → TERMINATED
```

- **`NEW`**: the `Thread` object exists, but `start()` hasn't been called —
  no OS thread has been created yet.
- **`RUNNABLE`**: eligible to run. This is the state most people get wrong —
  it does **not** mean "is currently executing on a core right now." A
  `RUNNABLE` thread might be running, or it might be sitting in the OS
  scheduler's ready queue waiting its turn. Java simply doesn't distinguish
  "running" from "ready to run"; both collapse into `RUNNABLE`.
- **`BLOCKED`**: waiting to acquire an intrinsic (`synchronized`) monitor
  that another thread currently holds. This is specifically about lock
  contention — nothing else produces `BLOCKED`.
- **`WAITING`**: waiting indefinitely for another thread to do something —
  `Object.wait()` with no timeout, `Thread.join()` with no timeout,
  `LockSupport.park()`. There's no clock involved; only another thread's
  action can release it.
- **`TIMED_WAITING`**: the same idea as `WAITING`, but with a deadline —
  `Thread.sleep(ms)`, `wait(timeout)`, `join(timeout)`. It resumes on
  whichever comes first: the condition, or the clock.
- **`TERMINATED`**: `run()` has returned (normally or via an uncaught
  exception). This is permanent — a `Thread` object cannot be restarted.

`ThreadLifecycleDemo` earns its place here because it doesn't just assert
this table, it *produces* every state on demand: it parks a thread on a
`synchronized` block held by another thread to get `BLOCKED`, awaits an
uncounted `CountDownLatch` to get `WAITING`, and sleeps to get
`TIMED_WAITING` — three different *mechanisms*, one per bucket, so the
distinction between "blocked on a lock" and "waiting on a signal" stops
being an abstract taxonomy and becomes three concrete scenarios you've
watched happen.

```java
// BLOCKED: two threads contend for the same monitor
Thread holder = new Thread(() -> {
    synchronized (lock) {
        lockHeld.countDown();
        releaseLock.await(); // holds the monitor the whole time
    }
});
Thread blocked = new Thread(() -> {
    synchronized (lock) {           // this call sits here...
        System.out.println("acquired");
    }
});
// ...while `holder` has the monitor, blocked.getState() == BLOCKED
```

### Daemon vs. non-daemon: who keeps the JVM alive

A non-daemon thread is a promise the JVM keeps: "I will not exit while you
are still running." A daemon thread carries no such promise — the JVM will
kill it mid-instruction the moment every non-daemon thread has finished,
with no cleanup, no `finally` block guaranteed to run. `DaemonThreadDemo`
makes the asymmetry vivid: an infinite-looping daemon thread is silently cut
off the instant `main()`'s short non-daemon thread finishes and the JVM
decides to exit — the loop simply stops mid-tick, never printing again.
Background/housekeeping threads (metrics pollers, cache evictors) are
usually daemons for exactly this reason: you don't want a forgotten
housekeeping thread to be the reason your JVM never exits.

### `join()` and `interrupt()`: waiting for and asking a thread to stop

`join()` blocks the calling thread until the target thread terminates —
`join(timeout)` blocks for at most that long, after which you must check
`isAlive()` yourself, because the call gives no other signal for "I gave up
waiting" versus "it actually finished." `ThreadJoinAndInterruptDemo` shows
why the timeout matters: joining without one on a thread that might never
finish is how a "batch job" becomes a "hang."

`interrupt()` does not stop a thread. It sets a boolean flag and, if the
thread is currently blocked in an interruptible call (`sleep`, `wait`,
`join`, blocking I/O), causes that call to throw `InterruptedException` and
**clears the flag as it throws it**. That last detail is the trap:

```java
try {
    Thread.sleep(10_000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // put the flag back
    // ...then actually stop what you were doing
}
```

If you catch `InterruptedException` and do nothing (or just log it), you've
silently thrown away the one piece of information the rest of the program
had for "please stop cooperatively" — the calling code that eventually
checks `Thread.currentThread().isInterrupted()` will find nothing there,
and cancellation quietly fails to propagate.

## Misconceptions worth naming directly

- **Belief: "`RUNNABLE` means it's running right now."**
  Wrong, because Java's state model doesn't have a separate "currently
  executing on a core" state — a `RUNNABLE` thread is often just sitting in
  the OS run queue. Proof: on a single-core machine, spawn 50 `RUNNABLE`
  busy-loop threads and inspect their states — all 50 report `RUNNABLE`
  simultaneously, even though at most one is physically executing at that
  instant.

- **Belief: "More threads means more throughput."**
  Wrong once thread count exceeds available cores for CPU-bound work — each
  additional thread adds scheduling and context-switch overhead without
  adding execution capacity. Proof: `ContextSwitchCostDemo`'s 50-thread run
  does the identical total work as its 1-thread run, slower.

- **Belief: "Catching `InterruptedException` and ignoring it is harmless
  since my thread keeps running."**
  Wrong, because the interrupt flag was the *only* carrier of the
  cancellation request; swallowing it means any later code that polls
  `isInterrupted()` to decide whether to stop will never see the request.
  Proof: wrap a long loop's `sleep()` in a bare `catch (InterruptedException
  e) {}` and call `interrupt()` from outside — the loop runs to completion
  regardless, because nothing ever recorded that the interrupt happened.

- **Belief: "A daemon thread will get a chance to clean up before the JVM
  exits."**
  Wrong — daemon threads are terminated abruptly, with no guarantee any
  `finally` block runs. Proof: `DaemonThreadDemo`'s daemon thread is mid-loop
  when the JVM exits; it never reaches any code after the loop, because
  there is no "after."

## Where this shows up for real

Every thread pool (module 08), every JVM-hosted server, and every actor or
event-loop framework is built on exactly this lifecycle underneath — when a
profiler or `jstack` thread dump reports a thread as `BLOCKED` or `WAITING`
(module 17), it is reading this exact state machine, not some higher-level
abstraction. Understanding `RUNNABLE` vs. `BLOCKED` vs. `WAITING` is what
lets you read a real production thread dump and immediately know whether a
stuck request is contending on a lock, waiting on I/O, or waiting on another
thread's signal.

## Check yourself

1. A thread reports state `RUNNABLE` on an 8-core machine with 40 busy
   threads. Is it definitely executing on a core right now? Why or why not?
2. Two threads both call `synchronized(lock) { ... }` on the same object.
   One gets in; what state does the JVM report for the other, and would
   that state be different if instead it were waiting on a
   `CountDownLatch.await()` with no timeout?
3. You call `thread.interrupt()` on a thread currently blocked in
   `Thread.sleep()`. Name the two things that happen to that thread as a
   direct result.
4. Why is `join(timeout)` alone not enough to know whether a thread
   finished — what extra call do you need, and why?

---

<details>
<summary>Answers</summary>

1. Not necessarily — `RUNNABLE` only means "eligible to run" in Java's
   model; with 40 threads and 8 cores, at most 8 can be physically executing
   at once, so most `RUNNABLE` threads are actually sitting in the OS's
   ready queue.
2. The losing thread is `BLOCKED` — specifically because it's contending on
   an intrinsic monitor. `CountDownLatch.await()` with no timeout produces
   `WAITING` instead, because that's cooperative waiting on a signal, not
   lock contention — different mechanism, different state.
3. Its interrupt status flag is set to true, and its blocked `sleep()` call
   immediately throws `InterruptedException` — while doing so, the JVM
   clears the flag again, which is why the handler must call
   `Thread.currentThread().interrupt()` if it wants the flag to survive.
4. `join(timeout)` returns either way once the timeout elapses, with no
   signal distinguishing "it finished" from "I gave up waiting" — you must
   call `isAlive()` afterward to tell which one happened.

</details>

---

← [Index](README.md) | Next → [02. Race Conditions & `synchronized`](02-race-conditions-and-synchronized.md)
