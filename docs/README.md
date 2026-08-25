# Concurrency, Built to Remember

This is a companion to the runnable curriculum in `concurrency-lab/` —
written for long-term retention, not a quick skim. Every topic follows the
same shape: the failure that motivates it, one concrete mental model, the
mechanism explained from first principles with real code traced
step-by-step, misconceptions stated as belief-vs-correction, and a
closed-book self-test. Read start to finish for the full story — each
module solves the problem the previous one wasn't quite good enough for —
or jump straight to whichever gap you already know you have.

## The mental model for each topic, in one line

| # | Topic | The one idea to keep |
|---|-------|----------------------|
| [01](01-thread-fundamentals.md) | Thread Fundamentals | A thread is a worker with its own private call stack; its lifecycle is a precise state machine, not a mood — `RUNNABLE` means *eligible*, not *executing*. |
| [02](02-race-conditions-and-synchronized.md) | Race Conditions & `synchronized` | `count++` is three steps, not one; a monitor is one key to one room — mutual exclusion *and* a happens-before edge, not just "no two threads at once." |
| [03](03-java-memory-model-and-volatile.md) | JMM & `volatile` | Each core keeps a private notebook, not a shared whiteboard; `volatile` is the instruction to actually look at the whiteboard, and it drags every earlier write along with it. |
| [04](04-atomics-and-cas.md) | Atomics & CAS | CAS swaps a value only if it still looks unchanged — but "looks unchanged" isn't "nothing happened," which is exactly the ABA problem. |
| [05](05-explicit-locks.md) | Explicit Locks | `synchronized` is one fixed policy; `j.u.c.locks` is the same room with a doorbell, a timer, separate call-buttons, and — with `StampedLock` — a way to peek without even knocking. |
| [06](06-concurrent-collections.md) | Concurrent Collections | A thread-safe object is not the same as a thread-safe *sequence* of calls on it — collapse "read, decide, write" into one atomic call. |
| [07](07-producer-consumer-blocking-queues.md) | Producer/Consumer Queues | A bounded queue is a dam, not a pipe — backpressure via a blocked `put()`, not silent unbounded growth. |
| [08](08-executors-and-thread-pools.md) | Executors & Thread Pools | A pool has a strict, four-step admission order — new thread, then queue, then another new thread, then reject — and each `Executors` factory is just a different point on that same policy. |
| [09](09-coordination-utilities.md) | Coordination Utilities | Five different shapes of rendezvous, distinguished by two questions: does it reset, and can the party count change? |
| [10](10-futures-and-completablefuture.md) | Futures & CompletableFuture | A `Future` is a receipt you can only redeem by waiting in line; `CompletableFuture` is a pipeline of callbacks that runs itself. |
| [11](11-fork-join-and-parallel-streams.md) | Fork/Join & Parallel Streams | Split like a recursive outline, but know when to stop; every parallel stream shares one process-wide pool, so a blocking call anywhere starves work everywhere. |
| [12](12-lock-free-structures.md) | Lock-Free Structures | Retry instead of block — and when there's structurally only one writer, you don't even need to retry. |
| [13](13-deadlock-livelock-starvation.md) | Deadlock, Livelock & Starvation | Three different flavors of "stuck": a permanent standoff, a busy-but-going-nowhere symmetry, and an unfair policy that's merely rare, not impossible. |
| [14](14-virtual-threads-and-structured-concurrency.md) | Virtual Threads & Structured Concurrency | Cheap concurrency for blocking work, not more parallelism — and a virtual thread can only get off the highway if it isn't holding a `synchronized` monitor while it blocks. |
| [15](15-reactive-webflux.md) | Reactive (Project Reactor) | Backpressure without a single blocked thread — the consumer says "give me N," and the producer is contractually forbidden from sending more. |
| [16](16-concurrency-design-patterns.md) | Concurrency Design Patterns | Five recurring shapes — rate limiter, bounded pool, actor, singleton, circuit breaker — each with one correct, off-the-shelf answer. |
| [17](17-performance-and-observability.md) | Performance & Observability | The JVM already keeps score on every thread's state and every lock's contention — you just have to ask, and ask while the thread is still alive. |
| [18](18-capstone-order-matching-engine.md) | Capstone: Order Matching Engine | An entire system's correctness argument reduced to "there is no second writer" — every other design decision is a named, deliberate application of an earlier module. |
| [19](19-benchmarks.md) | Benchmarks | A stopwatch around a `for` loop measures JIT warm-up, not your algorithm — ask JMH instead, and trust relative comparisons only. |
| [🧠](99-retrieval-practice.md) | Retrieval Practice | Closed-book, mixed order, revisited later — the point isn't reading the answer, it's the effort of trying to recall it first. |

## How it all connects: the capstone as a map of the whole curriculum

The capstone (module 18) is worth re-reading once you've been through every
module, because its cross-reference table is the single artifact in this
repo that ties the entire curriculum into one coherent design — a genuinely
useful structure to have memorized for "design a concurrent system" style
questions:

| Design decision | Module | The reasoning that makes it the right tool |
|---|---|---|
| Zero locks inside `OrderBook` | 12, 16 | The single-writer/actor principle removes the *need* for synchronization — no CAS, no lock, because there is provably never a second writer. Not a clever lock; the absence of a reason to lock at all. |
| Bounded `ArrayBlockingQueue` mailbox | 07 | Backpressure per symbol: a slow-to-match symbol never grows memory without bound — the cost of overload is a blocked caller, not a leak. |
| One virtual thread per symbol (and per HTTP request) | 14 | The system wants potentially thousands of dedicated, mostly-blocked-on-`take()` threads; virtual threads make that cheap where platform threads would exhaust the OS. |
| `ConcurrentHashMap.computeIfAbsent` for lazy per-symbol engines | 06 | Atomic "create exactly one engine per symbol" — with the module's own warning (keep the mapping function fast) directly shaping the implementation, since it runs under the map's bin lock. |
| `LongAdder` for hot per-order counters | 04 | Write-heavy, high-contention counters — exactly the profile `LongAdder` wins on. |
| `AtomicLong` for the order-id sequence | 04 | Needs the exact, immediately-usable value from every call — the opposite requirement from an eventually-consistent aggregate. |
| Graceful `@PreDestroy` shutdown per actor | 08 | The same `shutdown`/interrupt discipline as any executor, applied once per symbol instead of once per pool. |
| Correctness proven under load, not eyeballed | 09 | Coordinated, deterministic concurrent testing — latches and bounded waits, not `Thread.sleep` and hope. |

The pattern worth internalizing, independent of trading systems entirely:
**every hard concurrency problem is usually several already-solved shapes
stacked together**, not one clever new trick. Recognizing which shape
you're looking at — a bounded queue, a single-writer actor, a CAS retry
loop, a fair-vs-unfair lock trade-off — is the actual skill; the locking
code itself is almost always the easy part once the shape is right.

## One last skim before you walk in

If you only have a few minutes left, the highest-density material in this
whole set is [`99-retrieval-practice.md`](99-retrieval-practice.md) — it's
built to be revisited cold, without the surrounding explanation, which is
exactly the condition an interview question arrives under too.
