← [19. Benchmarks](19-benchmarks.md) | **Retrieval Practice**

# Retrieval Practice — Mixed, Closed-Book

This appendix pulls one round of questions from every topic file, but not
in module order — retrieval practice works better when you can't lean on
"whatever I just read" to answer the next question. Cover the answers,
answer from memory, then check yourself. Coming back to this file a week
or a month after first reading the topic files is exactly when this
exercise pays off most — that's the point of spaced retrieval: the harder
it feels to recall, the more it's actually strengthening the memory.

Each question links back to its source file if you need to re-derive it
from the full explanation.

---

## Set A — Foundations: threads, memory, and atomics

**1. (from [04](04-atomics-and-cas.md))** Describe a concrete interleaving
of two threads that produces the ABA problem on a plain
`AtomicReference`-based stack, and explain exactly what
`AtomicStampedReference` adds that prevents it.

**2. (from [01](01-thread-fundamentals.md))** A thread reports state
`RUNNABLE` on an 8-core machine with 40 busy threads. Is it definitely
executing on a core right now? Why or why not?

**3. (from [03](03-java-memory-model-and-volatile.md))** Why does making a
reference field `volatile` make it safe to publish a fully-built immutable
object to another thread, when making a `count` field `volatile` does
*not* make `count++` safe?

**4. (from [02](02-race-conditions-and-synchronized.md))** Why must the
condition around a `wait()` call be checked in a `while` loop rather than
an `if`, even when you're confident you'll only ever call `notify()` (not
`notifyAll()`) correctly?

**5. (from [01](01-thread-fundamentals.md))** You call `thread.interrupt()`
on a thread currently blocked in `Thread.sleep()`. Name the two things
that happen to that thread as a direct result.

<details><summary>Answers</summary>

1. Thread 1 reads `top=A`, plans to CAS to `A.next=B`, then pauses. Thread
   2 really pops `A` then `B` (top becomes `C`), then pushes the same `A`
   object back onto the stack. Thread 1 resumes and its CAS(`A`→`B`)
   succeeds because `top` does equal `A` again — corrupting the stack.
   `AtomicStampedReference` adds a stamp that increments on every mutation,
   so even though the reference reads as `A` again, the stamp has moved —
   Thread 1's CAS correctly fails instead of succeeding.
2. Not necessarily — `RUNNABLE` only means "eligible to run"; with 40
   threads and 8 cores, at most 8 can be physically executing at once, so
   most `RUNNABLE` threads are sitting in the OS's ready queue.
3. Because the happens-before edge from the volatile reference write
   carries along every plain write that happened *before* it in program
   order (the constructor's field writes) — but `count++` is itself a
   read-modify-write, and `volatile` only makes each individual read or
   write visible, not the three-step sequence atomic.
4. A spurious wakeup or a beaten-to-the-punch waiter both leave the
   condition just as false as before `wait()` returned — `if` would
   barrel ahead on stale information; `while` re-checks and waits again.
5. Its interrupt status flag is set to true, and its blocked `sleep()` call
   immediately throws `InterruptedException` — while doing so, the JVM
   clears the flag again, which is why the handler must call
   `Thread.currentThread().interrupt()` if it wants the flag to survive.

</details>

---

## Set B — Locks, collections, and queues

**6. (from [06](06-concurrent-collections.md))** Why can
`if (!map.containsKey(k)) map.put(k, v)` produce more than one "winner"
even when `map` is a genuinely thread-safe `ConcurrentHashMap`?

**7. (from [05](05-explicit-locks.md))** Why is it dangerous for the same
thread to call `stampedLock.readLock()` from within a method that's
already holding that same lock?

**8. (from [07](07-producer-consumer-blocking-queues.md))** What must be
true for `SynchronousQueue.put()` to return, and how is that different from
`ArrayBlockingQueue.put()` on a queue with capacity 1?

**9. (from [05](05-explicit-locks.md))** Under a constant stream of
readers, why can a writer be starved with a default
`ReentrantReadWriteLock`?

**10. (from [06](06-concurrent-collections.md))** Why does
`CopyOnWriteArrayList` iteration never throw `ConcurrentModificationException`,
and what's the real cost you pay in exchange?

<details><summary>Answers</summary>

6. `containsKey` and `put` are each atomic on their own, but the *sequence*
   is not — another thread can execute its own `containsKey` in the gap
   between this thread's `containsKey` and its `put`, so both can observe
   "absent" and both proceed to write.
7. `StampedLock`, unlike `ReentrantLock`/`ReentrantReadWriteLock`, is not
   reentrant — a thread trying to acquire it again while it already holds
   it will block waiting for itself to release it first, which never
   happens: a self-deadlock.
8. `SynchronousQueue.put()` only returns once another thread is already (or
   concurrently) blocked in `take()` to receive that exact element — there
   is no internal buffer at all. `ArrayBlockingQueue` with capacity 1 can
   accept one `put()` and return immediately even with no consumer present
   yet, since that one slot buffers it.
9. The default policy is unfair and not writer-preferring — as long as
   readers keep arriving, there's no built-in mechanism forcing the lock to
   eventually favor a waiting writer.
10. Each iterator holds a reference to one fixed array snapshot taken at
    creation time, and mutation always creates an entirely new array —
    the iterator is structurally unable to observe a later write. The cost
    is an O(n) full-array copy on every single add/remove/set.

</details>

---

## Set C — Executors, coordination, and async composition

**11. (from [08](08-executors-and-thread-pools.md))** Why can
`Executors.newFixedThreadPool(4)`'s task count climb far beyond 4 with no
error or rejection?

**12. (from [10](10-futures-and-completablefuture.md))** Contrast what
`exceptionally`, `handle`, and `whenComplete` each do when the upstream
future fails — which ones can change what the future ultimately completes
with?

**13. (from [09](09-coordination-utilities.md))** Why must a `Phaser`
party call `arriveAndDeregister()` instead of just `arrive()` once it's
done participating for good?

**14. (from [08](08-executors-and-thread-pools.md))** Why does
`CallerRunsPolicy` act as a form of backpressure, rather than just another
way to "handle" a rejected task?

**15. (from [10](10-futures-and-completablefuture.md))** Why does chaining
a function that itself returns a `CompletableFuture` require
`thenCompose` instead of `thenApply`?

**16. (from [09](09-coordination-utilities.md))** What exactly does a
`Semaphore` guarantee, and why is that different from what a `Lock`
provides?

<details><summary>Answers</summary>

11. `newFixedThreadPool` backs its threads with an **unbounded** queue —
    tasks beyond what the threads can immediately run simply queue up with
    no limit and no rejection.
12. `exceptionally` only runs on failure and can change the outcome.
    `handle` always runs and can also change the outcome. `whenComplete`
    always runs but its return value is discarded — it can only observe,
    never recover; the original exception still propagates.
13. Because a `Phaser` keeps waiting for an arrival from every registered
    party before advancing — a party that never deregisters stays
    registered, stalling every future phase waiting for an arrival that
    will never come.
14. It makes the *submitting* thread execute the rejected task itself,
    synchronously — that thread is now busy doing work instead of
    submitting more, naturally slowing the rate of new submissions.
15. `thenApply` would wrap the inner `CompletableFuture` inside an outer
    one, producing a nested `CompletableFuture<CompletableFuture<T>>`;
    `thenCompose` flattens the two into one.
16. A `Semaphore` guarantees at most N permits are checked out concurrently
    — it tracks a count, not ownership; any thread may `release()`
    regardless of whether it ever `acquire()`d. A `Lock` additionally
    guarantees only the thread that acquired it may release it.

</details>

---

## Set D — Parallelism and lock-free structures

**17. (from [11](11-fork-join-and-parallel-streams.md))** Explain
concretely how a blocking `Thread.sleep()` inside one parallel stream can
slow down a completely unrelated parallel stream elsewhere in the same
JVM.

**18. (from [12](12-lock-free-structures.md))** Why does this particular
`TreiberStack` implementation avoid the ABA problem, and what specific
change would reintroduce it?

**19. (from [11](11-fork-join-and-parallel-streams.md))** Why does
recursively forking a task all the way down to single-element pieces
typically make it *slower*, not faster?

**20. (from [12](12-lock-free-structures.md))** Why is no CAS or lock
needed on `head`/`tail` in the SPSC ring buffer, when a multi-producer
version of the same buffer would need one?

**21. (from [12](12-lock-free-structures.md))** Under what condition does
a lock-based structure actually outperform a lock-free one?

<details><summary>Answers</summary>

17. Parallel streams draw worker threads from one shared, process-wide
    `ForkJoinPool.commonPool()` by default. If one stream's per-element
    work blocks, it occupies worker threads for the duration — any other
    parallel stream anywhere in the same JVM has to wait for a worker to
    free up, even though its work is unrelated.
18. Every `push` allocates a brand-new `Node` object — no node is ever
    reused, so a reference can never legitimately reappear as `top` after
    removal. Introducing a node pool/freelist that reuses `Node` objects
    would reintroduce the ABA scenario.
19. Every fork creates and schedules a real task object; below some
    threshold, the bookkeeping overhead of managing enormous numbers of
    trivial tasks outweighs whatever parallel speedup they add.
20. Exactly one thread ever writes `tail` (the producer) and exactly one
    ever writes `head` (the consumer) — there is never a writer-writer race
    to resolve. A multi-producer version would need CAS or a lock to
    resolve concurrent writers to `tail`.
21. When critical sections are long, or contention is so extreme that CAS
    retries themselves become expensive (repeated re-reads, cache-line
    bouncing) — a blocked thread that parks and burns no CPU can then
    outperform a thread endlessly retrying a losing CAS.

</details>

---

## Set E — Failure modes: deadlock, livelock, starvation

**22. (from [13](13-deadlock-livelock-starvation.md))** Why is a deadlock
visible in a `jstack` dump, while a livelock is not?

**23. (from [13](13-deadlock-livelock-starvation.md))** A thread acquires
an unfair lock only 41 times in 1.5 seconds while six competitors acquire
it a combined 58,900 times. Is this thread deadlocked? What is it actually
experiencing?

**24. (from [13](13-deadlock-livelock-starvation.md))** In a livelock fix,
why does a *fixed* backoff delay fail while a *randomized* one succeeds?

**25. (from [17](17-performance-and-observability.md))** Why does
`ThreadDumpAnalysisDemo` poll `findDeadlockedThreads()` in a loop with a
timeout, instead of calling it once immediately after starting the
suspect threads?

<details><summary>Answers</summary>

22. In a genuine deadlock, both threads are truly blocked waiting to
    acquire a monitor, which the JVM can detect and report. In a livelock,
    no thread is ever blocked on a monitor — every thread is actively
    executing (checking, backing off, retrying) — so there's nothing for a
    deadlock detector to find.
23. Not deadlocked — starving. It's not permanently blocked (it does
    acquire the lock sometimes); it's disproportionately, persistently
    out-competed under a policy that lets other threads repeatedly barge
    ahead of it.
24. With a fixed delay, both threads back off and retry on exactly the same
    cadence, so they keep colliding at the same instant forever — the
    symmetry never breaks. A randomized delay makes it likely one thread's
    wait ends before the other's, breaking the lockstep.
25. A deadlock takes a moment to actually form; checking once, immediately,
    risks running before the cycle has formed. Polling for a bounded
    window is reliable because once a deadlock does form, it's a stable
    condition that persists.

</details>

---

## Set F — Modern concurrency: virtual threads and reactive streams

**26. (from [14](14-virtual-threads-and-structured-concurrency.md))** Why
does blocking inside a `synchronized` block pin a virtual thread's
carrier, while blocking while holding a `ReentrantLock` does not?

**27. (from [15](15-reactive-webflux.md))** Contrast how a bounded
`BlockingQueue` creates backpressure with how a `Flux`'s `request(n)`
protocol creates it.

**28. (from [14](14-virtual-threads-and-structured-concurrency.md))** Under
what kind of workload would switching to virtual threads provide little or
no benefit?

**29. (from [15](15-reactive-webflux.md))** If an upstream `map` step has a
side effect and the pipeline uses `.retry(2)`, what happens to that side
effect on a retry?

**30. (from [14](14-virtual-threads-and-structured-concurrency.md))** What
specifically is *not* guaranteed by the manual `try-with-resources`
`ExecutorService` approximation of structured concurrency, compared to the
real `StructuredTaskScope.ShutdownOnFailure`?

<details><summary>Answers</summary>

26. Releasing and reacquiring monitor state across an unmount/remount
    isn't supported by the JVM's monitor implementation, so a virtual
    thread blocked while holding a `synchronized` monitor can't be safely
    unmounted — the carrier stays occupied. `j.u.c` locks are implemented
    to cooperate with the scheduler, so blocking while holding one still
    allows a normal unmount.
27. A `BlockingQueue` blocks the producer's *thread* inside `put()`. A
    `Flux` creates backpressure through a demand signal — the producer
    thread is never blocked; it simply doesn't emit past what's been
    requested.
28. CPU-bound workloads — virtual threads add cheap concurrency for
    blocking operations, not additional parallelism; continuously
    computing work still needs a carrier the whole time regardless.
29. The side effect runs again — `retry(n)` re-subscribes to the entire
    upstream chain from the beginning, so every upstream operator,
    including the one with the side effect, re-executes on each attempt.
30. Sibling subtasks are only cancelled once the hand-written loop's
    `future.get()` happens to encounter a failure, which can lag behind
    the actual failure. `ShutdownOnFailure` cancels every sibling
    immediately when any one subtask fails.

</details>

---

## Set G — Patterns, observability, and systems design

**31. (from [16](16-concurrency-design-patterns.md))** What specifically
does `volatile` fix in the double-checked locking singleton, and why does
the Holder idiom not need it at all?

**32. (from [18](18-capstone-order-matching-engine.md))** Why does
`OrderBook` need zero locks or atomics to be correct, when a `TreeMap` and
a plain `long` field would normally be a textbook race condition?

**33. (from [17](17-performance-and-observability.md))** What's the
difference between what `blockedCount`/`blockedTime` measure versus what
`waitedCount`/`waitedTime` measure on a `ThreadInfo`?

**34. (from [16](16-concurrency-design-patterns.md))** Why must a circuit
breaker in `HALF_OPEN` state allow only one trial call at a time?

**35. (from [19](19-benchmarks.md))** Why can a single, unwarmed
`System.nanoTime()`-based timing produce a misleading result, even when
the code being measured is completely correct?

**36. (from [18](18-capstone-order-matching-engine.md))** Why does the
capstone's order-id generator use `AtomicLong` while its
orders-processed counter uses `LongAdder`?

<details><summary>Answers</summary>

31. `volatile` establishes a happens-before edge between the write that
    publishes the constructed instance and any subsequent read, guaranteeing
    a reader that sees a non-null reference sees the fully-constructed
    object. The Holder idiom relies on a different guarantee entirely — the
    JVM's own class-initialization guarantee — needing no manual
    happens-before edge.
32. `OrderBook.match()`/`snapshot()` are only ever called from one specific
    thread — the `SymbolEngine`'s dedicated worker — for the book's entire
    lifetime. There is no second writer to ever race against.
33. `blockedCount`/`blockedTime` measure time spent waiting to *enter* a
    monitor (lock contention); `waitedCount`/`waitedTime` measure time
    spent in cooperative waiting (`wait()`/`join()`/parking).
34. Letting multiple concurrent trial calls through would flood a
    potentially still-failing dependency with exactly the load the breaker
    exists to prevent; gating with a CAS ensures exactly one trial is in
    flight at a time.
35. A single run can mix interpreted execution, partial JIT compilation,
    and full optimization into one measurement, and can be skewed by an
    incidental GC pause — none of which reflects steady-state performance.
36. `AtomicLong` gives back the exact, immediately usable value from every
    increment, needed since each order must receive its precise assigned
    ID right away. `LongAdder` trades that away for higher write
    throughput, fitting a counter only ever read in aggregate.

</details>

---

← [19. Benchmarks](19-benchmarks.md) | [Back to Index](README.md)
