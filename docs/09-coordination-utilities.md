← [08. Executors & Thread Pools](08-executors-and-thread-pools.md) | **09. Coordination Utilities** | Next → [10. Futures & CompletableFuture](10-futures-and-completablefuture.md)

# 09 — Coordination Utilities

## The failure, first

Four independent workers each take a variable amount of time to initialize
(connect to a DB, warm a cache, load config). A coordinator thread needs to
proceed only once *all four* are ready — not after a guessed
`Thread.sleep(500)` that might be too short under load and wastefully long
otherwise. You could build this yourself with a shared counter and a
`wait()`/`notifyAll()` loop (module 02's tools), re-deriving the same
"count down to zero, then release everyone waiting" logic every time this
shape of problem comes up. `CountDownLatch` is that logic, already built,
tested, and named — but it only solves *this* shape of problem. The
interesting failure in this module isn't a bug, it's reaching for the
wrong coordination primitive: using a `CountDownLatch` where you actually
need repeated rounds, or a `CyclicBarrier` where the number of participants
needs to change mid-flight — each of the five tools here is fixed to
exactly one shape, and no amount of cleverness makes a one-shot latch
behave like a reusable barrier.

## Mental model: five different kinds of rendezvous point

Every tool in this module answers "make threads wait for each other," but
they differ along two axes worth memorizing directly: **does it reset**
and **can the number of participants change?**

| Utility | Reusable? | Party count | Shape of problem it answers |
|---|---|---|---|
| `CountDownLatch` | No — one-shot | Fixed at construction | "Wait until N things happen, once, ever" |
| `CyclicBarrier` | Yes — resets automatically | Fixed at construction | "N threads sync at the same point, every round" |
| `Semaphore` | Yes | N/A — permits, not threads | "At most N concurrent users of a resource" |
| `Exchanger` | Yes | Exactly 2 | "Swap a buffer between exactly two threads" |
| `Phaser` | Yes | Dynamic — register/deregister | "Multi-phase work where who's participating changes" |

Picture a `CountDownLatch` as a gate that opens once and stays open
forever — useless for a second race. A `CyclicBarrier` is a gate that
opens, then immediately resets itself for the next round, as long as the
same fixed head-count shows up every time. A `Semaphore` isn't a gate for
specific threads at all — it's a parking garage with a fixed number of
tickets at the entrance; any thread can take a ticket (permit) and any
thread can return one, and the garage doesn't track *who* is parked where,
only how many spots remain. An `Exchanger` is a hallway with room for
exactly two people to swap what they're carrying and walk away — no more,
no fewer. A `Phaser` is the only gate that lets you add or remove
participants *between* rounds, which is exactly the capability the other
four structurally lack.

## Concept, from first principles

### `CountDownLatch`: two symmetric patterns, one mechanism

`CountDownLatchDemo` shows the same primitive solving two mirror-image
problems:

```java
// pattern 1: coordinator waits for workers to become READY
CountDownLatch readyLatch = new CountDownLatch(workerCount);
// each worker, once initialized: readyLatch.countDown();
readyLatch.await();   // coordinator blocks here until count reaches 0

// pattern 2: coordinator waits for workers to FINISH
CountDownLatch doneLatch = new CountDownLatch(workerCount);
// each worker, once done: doneLatch.countDown();
doneLatch.await();
```

Both patterns are the identical mechanism — a counter that only ever
decreases, and any number of threads blocked in `await()` are all released
the instant it hits zero. The one-shot nature is not a limitation to work
around; it's the entire point of a *startup* or *completion* gate — you
genuinely never want to "reopen" a completion signal for work that already
finished.

### `CyclicBarrier`: the same gate, reused every round, with an action attached

`CyclicBarrierDemo` has three workers repeatedly contribute a value, then
wait at a barrier, for three rounds:

```java
CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
    // runs exactly once per round, after the LAST party arrives
    int total = roundResults.stream().mapToInt(Integer::intValue).sum();
    System.out.println("round complete, aggregate=" + total);
});
// each worker, each round:
roundResults.add(contribution);
barrier.await();   // blocks until all `parties` threads have called await() this round
```

Two details matter more than they look: the barrier action runs on
**whichever thread happens to arrive last** — there's no dedicated
"coordinator" thread running it, so it must be safe to run on an arbitrary
worker thread. And the barrier **automatically resets** after every round,
which is the entire reason it's called "cyclic" — the same three parties
can synchronize again for round 2 without constructing anything new. The
sharp edge: if any single party is interrupted or times out while waiting,
`BrokenBarrierException` is thrown to **every other thread** waiting at
that barrier — one thread's failure poisons the whole round for everyone,
because the barrier can no longer guarantee all parties will ever arrive.

### `Semaphore`: bounding a count, not guarding a critical section

`SemaphoreResourcePoolDemo` models a connection pool with only 2 real slots
and 5 clients wanting one:

```java
Semaphore permits = new Semaphore(2, /* fair= */ true);
permits.acquire();     // blocks if 0 permits available
try {
    // use the resource
} finally {
    permits.release();
}
```

This looks like a lock, but it is a fundamentally different guarantee: a
`Lock` enforces "exactly one thread in the critical section, and only the
thread that acquired it may release it." A `Semaphore` enforces "at most N
permits are checked out concurrently" — any thread can `release()` a
permit, even one that never called `acquire()`, and the semaphore has no
concept of "ownership" at all. `fair=true` grants permits roughly in
arrival order rather than letting a fresh, already-scheduled thread barge
ahead of one that's been waiting — the same fairness/throughput trade-off
from module 05's `ReentrantLock`, applied to a counting resource instead of
a single lock.

### `Exchanger`: a rendezvous for exactly two, swapping in both directions at once

`ExchangerDemo` has a producer filling a buffer and a consumer draining it,
trading buffers back and forth every round:

```java
buffer = exchanger.exchange(buffer);
```

Each call blocks until *the other* thread also calls `exchange()`, at which
point both threads' arguments are swapped — the producer hands over a full
buffer and receives back an empty one to refill, in a single atomic
rendezvous. If either side simply never calls `exchange()` — a bug, a
crashed thread, a forgotten call in one code path — the other side blocks
forever; `Exchanger` has no timeout-free way to detect "my partner isn't
coming."

### `Phaser`: the one primitive that lets participants change mid-run

Neither a `CountDownLatch` nor a `CyclicBarrier` can change how many
parties they're waiting for once constructed. `PhaserDemo` demonstrates
exactly the scenario that forces you to reach for `Phaser` instead: three
workers participate in phase 0, and a **fourth worker joins only for
phase 1**:

```java
Phaser phaser = new Phaser(1);           // "owner" party keeps it alive
phaser.register();                       // add a new party dynamically
pool.submit(() -> {
    // ... do phase's work ...
    phaser.arriveAndDeregister();        // leave after this phase, don't block future ones
});
phaser.arriveAndAwaitAdvance();          // main waits for all currently-registered parties
```

Each worker calls `arriveAndDeregister()`, not just `arrive()` — because a
worker that only ever participates in *one* phase must remove itself
afterward, or the phaser will wait forever for an arrival from a party that
is never coming back. The demo keeps an explicit "owner" party (registered
in the constructor) specifically so the phaser doesn't terminate between
phase 0 and phase 1, when momentarily zero *worker* parties are registered
— the owner deregisters only at the very end, once no more phases are
coming.

## Misconceptions worth naming directly

- **Belief: "I can call `countDown()` past zero, or reuse a
  `CountDownLatch` for a second round by resetting its count."**
  Wrong — a `CountDownLatch` cannot be reset once it reaches zero; extra
  `countDown()` calls are simply no-ops, and a second round requires a
  brand-new latch. Reaching for a `CyclicBarrier` is the fix when you
  actually need repeated rounds with the same fixed party count.

- **Belief: "A `CyclicBarrier`'s action runs on a dedicated coordinator
  thread, so it's naturally isolated from the workers' own state."**
  Wrong — it runs on whichever of the `parties` threads happens to arrive
  at the barrier last, which is not deterministic across rounds; the
  action must be safe to execute on an arbitrary worker thread.

- **Belief: "A `Semaphore` provides mutual exclusion the same way a
  `Lock` does, just for more than one thread at a time."**
  Wrong — a semaphore tracks a count of permits, not which thread holds
  which one; any thread may `release()` regardless of whether it ever
  `acquire()`d, which is a fundamentally weaker (and more flexible)
  guarantee than lock ownership.

- **Belief: "If one thread times out or is interrupted at a
  `CyclicBarrier`, only that thread is affected — the others just keep
  waiting for the party count to be satisfied some other way."**
  Wrong — a single broken party throws `BrokenBarrierException` to every
  other thread already waiting at that barrier, since the barrier can no
  longer honestly promise all parties will arrive.

- **Belief: "A `Phaser` party that's done for good doesn't need to do
  anything special — the phaser will notice it's not coming back."**
  Wrong — a party that fails to call `arriveAndDeregister()` (using plain
  `arrive()` or nothing at all) leaves the phaser waiting for an arrival
  that will never happen, permanently stalling every future phase.

## Where this shows up for real

`CountDownLatch`-style "wait for N services to report healthy" is the
exact shape of a Kubernetes readiness gate or a distributed system's
startup barrier. `Semaphore`-bounded resource pools are the standard way to
cap concurrent outbound connections, file handles, or expensive external
API calls without blocking on a full lock. `CyclicBarrier`'s repeated-round
synchronization is the same shape as a distributed map-reduce's "wait for
all mappers to finish this round before starting the reduce phase." A
`Phaser`'s dynamic party count is exactly what's needed when the number of
worker threads in a multi-stage batch job can grow or shrink between
phases — something none of the fixed-party-count tools here can express at
all.

## Check yourself

1. Why can't a `CountDownLatch` be reused for a second round of
   coordination, and what's the direct replacement when you need repeated
   rounds with the same fixed set of participants?
2. Why must a `CyclicBarrier`'s barrier action be safe to run on *any* of
   the participating threads, rather than a dedicated coordinator?
3. What exactly does a `Semaphore` guarantee, and why is that a weaker (or
   at least different) guarantee than what a `Lock` provides?
4. Why does a worker in `PhaserDemo` call `arriveAndDeregister()` instead of
   just `arrive()` once it's done participating for good?
5. Given the five utilities in this table, which one is the only one that
   supports a dynamically changing number of participants across rounds,
   and what specifically about the other four rules that out?

---

<details>
<summary>Answers</summary>

1. Because its internal counter only ever decreases and it has no reset
   mechanism once it reaches zero — a `CyclicBarrier` is the direct
   replacement, since it automatically resets after every round as long as
   the same fixed number of parties keeps participating.
2. Because `CyclicBarrier` runs the action on whichever thread happens to
   be the one that satisfies the last `await()` call for that round — this
   varies round to round and isn't a separate, dedicated thread, so the
   action's code must tolerate running on any participant.
3. A `Semaphore` guarantees that at most N permits are checked out
   concurrently — it tracks a count, not ownership. A `Lock` additionally
   guarantees that only the thread that acquired it may release it (true
   mutual exclusion tied to a specific holder); a semaphore permit can be
   released by any thread, whether or not it ever acquired one.
4. Because a `Phaser` keeps waiting for an arrival from every currently
   registered party before it can advance to the next phase — a party that
   calls plain `arrive()` (or nothing) without deregistering stays
   registered, so every future phase stalls forever waiting for an arrival
   that will never come from a worker that's already done.
5. `Phaser` — `CountDownLatch` can't reset at all, and `CyclicBarrier`,
   `Semaphore` (which isn't party-based to begin with), and `Exchanger`
   (fixed at exactly two) all have a party count that's either fixed at
   construction or structurally limited, with no register/deregister
   mechanism for changing who's expected to participate between rounds.

</details>

---

← [08. Executors & Thread Pools](08-executors-and-thread-pools.md) | Next → [10. Futures & CompletableFuture](10-futures-and-completablefuture.md)
