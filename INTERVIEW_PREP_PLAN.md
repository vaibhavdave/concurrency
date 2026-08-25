# Plan: Interview-Prep Documentation

## Goal

A set of documents under `docs/` that teach every concurrency topic in this
repo well enough that the reader **still remembers the concept years later**
— not a cram sheet optimized for how fast it can be read. Depth, clarity,
and memorability are the only optimization targets; how long it takes to
read is not a design constraint and shouldn't shape any decision below.

This still means curating — not all 100+ demo classes make it in — but the
selection criterion changes: a topic keeps only the example(s) that build
real, lasting intuition (the one that makes the failure mode *click*, and
the one that makes the fix obviously correct), not the smallest excerpt that
technically illustrates the API. Where a second example earns its place by
deepening understanding rather than padding, include it — nothing here is
capped for brevity's sake.

Source of truth: this repo already has a `README.md` per module
(`m01`…`m17`, capstone, benchmarks) with a consistent structure — `Learning
Objectives`, `Concept`, `Common Pitfalls`, `Demos`, `Sample Output`. That
material is accurate and well-organized, but it's written as reference
documentation (terse, assumes you'll run the code). This plan turns it into
*teaching* documentation: the same truths, explained so they stick without
needing the code running in front of you.

## What makes a concept "stick" — the techniques every file uses

Concurrency concepts are notoriously easy to read and forget, because most
explanations state the mechanism without ever making the reader *feel* why
it's true. Every topic file is built from the same set of retention
techniques, applied deliberately:

1. **Lead with the failure, not the fix.** Show the bug happening first (a
   lost update, a stuck thread, a deadlock) before naming the concept that
   explains it. A fix without a felt problem is a fact; a fix for a problem
   you just watched happen is a memory.
2. **One concrete mental model per topic**, stated in physical, not
   computer-science, terms — e.g. `volatile` as "a shared whiteboard
   everyone is forced to re-read, not a private sticky note"; a monitor lock
   as "a single key to a single room"; a `Semaphore` as "a parking garage
   with N spots and a ticket at the gate." The model is what survives when
   the syntax is forgotten — it's what lets someone *rederive* the syntax
   later.
3. **Contrast, always paired.** Every topic shows the broken version and the
   fixed version side by side (not fix alone) — race vs. synchronized,
   `if` vs. `while` around `wait()`, unbounded pool vs. bounded pool,
   blocking call vs. `CompletableFuture` chain. The brain encodes
   differences far better than isolated facts.
4. **Trace execution, don't just paste code.** For the chosen example(s),
   walk through what happens on each line for two threads/tasks
   interleaving — not "here's the API," but "here's thread A doing X while
   thread B does Y, and here's exactly where it goes wrong or right."
5. **Anchor to a real system.** One or two sentences per topic naming where
   this exact mechanism shows up outside a classroom demo (e.g. CAS in
   database MVCC and `AtomicLong`; `CompletableFuture` chains in reactive
   HTTP clients; lock ordering in every deadlock post-mortem you'll ever
   read). Abstract mechanisms stick far better once tied to something the
   reader already believes matters.
6. **Name the misconception directly.** Pitfalls aren't listed as neutral
   bullet points — each is framed as "you will believe X; X is wrong
   because Y; here's the input that proves it." Correcting a belief you
   just noticed yourself holding is far stickier than reading a warning.
7. **End with retrieval, not summary.** Each file ends with a small number
   of questions the reader should be able to answer closed-book. Writing a
   summary the reader re-reads is passive; forcing recall is what actually
   builds long-term memory (the testing effect). Answers are provided
   (collapsed/at the bottom), but the act of trying first is the point.

## Deliverable shape

**`docs/` folder, one file per topic, plus an index:**

```
docs/
├── README.md                                          # index: map + mental models + "how it connects"
├── 01-thread-fundamentals.md
├── 02-race-conditions-and-synchronized.md
├── 03-java-memory-model-and-volatile.md
├── 04-atomics-and-cas.md
├── 05-explicit-locks.md
├── 06-concurrent-collections.md
├── 07-producer-consumer-blocking-queues.md
├── 08-executors-and-thread-pools.md
├── 09-coordination-utilities.md
├── 10-futures-and-completablefuture.md
├── 11-fork-join-and-parallel-streams.md
├── 12-lock-free-structures.md
├── 13-deadlock-livelock-starvation.md
├── 14-virtual-threads-and-structured-concurrency.md
├── 15-reactive-webflux.md
├── 16-concurrency-design-patterns.md
├── 17-performance-and-observability.md
├── 18-capstone-order-matching-engine.md
├── 19-benchmarks.md
└── 99-retrieval-practice.md                            # closed-book self-test, all topics
```

Numbered filenames (matching the existing module numbers) keep the folder
sorted in reading order in any file browser. Names otherwise mirror the
existing module folder names for easy cross-reference back to source.

### `docs/README.md` (the index page)

Framed as a map of the territory and how its parts relate, not a
last-minute skim:

1. **One-paragraph mental model per topic** — not a word-count-driven
   one-liner, but the actual analogy from that file, so the index alone
   conveys the shape of the whole curriculum.
2. **Table of contents** — links to all 19 topic files in order.
3. **"How it all connects"** — expanded version of the capstone README's
   table mapping design decisions back to modules, with the reasoning
   spelled out (why *this* module's idea was the right tool for *that*
   part of the engine) rather than just the mapping itself.
4. A pointer to `99-retrieval-practice.md`.

### Each numbered topic file (`01`…`19`), fixed template

- **The failure first**: a short, concrete scenario where the naive
  approach breaks, described in prose before any code.
- **Mental model**: the one physical analogy for this topic, stated
  plainly and reused consistently if it recurs in later topics (e.g. the
  "single key to a single room" lock model from M02 is referenced again,
  not re-invented, when M05's `ReentrantLock` generalizes it).
- **Concept, explained from first principles**: not a restatement of the
  source README's bullet points, but the reasoning that connects them —
  why the JVM/hardware behaves this way, not just that it does.
- **Worked example(s), traced step by step**: the broken version and the
  fixed version, both walked through interleaving-by-interleaving, drawn
  from the repo's real demo classes (linked back to the actual file) —
  chosen for how clearly they teach the mechanism, with no cap on how many
  are included if more than one genuinely deepens understanding.
- **Misconceptions / pitfalls**, each stated as belief → why it's wrong →
  the concrete input that breaks it. Condensed from, but not limited to,
  the source README's `Common Pitfalls`.
- **Where this shows up for real**: 1-2 sentences grounding the concept in
  a system the reader already knows matters.
- **Diagram** wherever it genuinely clarifies a sequence or state
  transition (not budgeted to a fixed subset of modules as before — reuse
  the repo's existing Mermaid diagrams and add new ones anywhere a
  sequence is easier to see than to read).
- **Check yourself**: 2-4 closed-book questions specific to this topic,
  answers given after a visual break (e.g. below a `---`), linking into the
  shared `99-retrieval-practice.md` for a later, mixed-topic pass.
- **Prev / Next** links, so the set still reads start-to-finish as one
  continuous story — the repo's own narrative ("every module solves the
  problem the previous one wasn't quite good enough for") is the spine
  that ties all 19 files together.

### `99-retrieval-practice.md`

A mixed, closed-book self-test pulling questions from every topic —
deliberately out of module order in places, since retrieval practice works
better when it's not just "answer the question right after reading the
answer." Meant to be revisited well after first reading (a week later, a
month later), which is the point at which spaced retrieval actually builds
long-term retention.

## Topic list and chosen worked example(s)

Examples are chosen for teaching value, not brevity — this list names the
canonical pair (broken vs. fixed, where one exists) that best makes each
concept click; a file may reference more of the module's demos in prose
where doing so adds real understanding.

| File | Module | Worked example(s) | Core mental model to build |
|---|--------|----------------------------------|------|
| `01` | Thread Fundamentals | `ThreadLifecycleDemo`, `ThreadJoinAndInterruptDemo` | A thread as an independent worker with its own call stack; states as a state machine, not a mood |
| `02` | Race Conditions & `synchronized` | `UnsafeCounter` vs `SafeSynchronizedCounter`, `WaitNotifyBoundedBuffer` | A monitor as one key to one room; `count++` as three separate steps two workers can interleave |
| `03` | JMM & `volatile` | `VisibilityProblemDemo` vs `VolatileFixDemo` | Per-core caches as private notebooks; `volatile`/happens-before as the rule for when a shared whiteboard gets re-read |
| `04` | Atomics & CAS | `AtomicCounterDemo`, `AbaProblemDemo` | CAS as "swap the lock only if it still looks like I left it"; ABA as the lock looking unchanged while the room was rearranged |
| `05` | Explicit Locks | `ReentrantLockBasicsDemo`, `ReadWriteLockDemo` | A lock you can hand back, try, time out on, or split into "many readers, one writer" |
| `06` | Concurrent Collections | `ConcurrentHashMapDemo`, `CompoundActionPitfallDemo` | Thread-safety per operation vs. per sequence of operations — the collection is safe, your two-step use of it might not be |
| `07` | Producer/Consumer Queues | `ArrayBlockingQueuePipelineDemo` | A queue as built-in backpressure — blocking instead of polling, no busy-wait |
| `08` | Executors & Thread Pools | `ThreadPoolExecutorTuningDemo`, `GracefulShutdownDemo` | A pool as a fixed crew plus a waiting line, with real decisions about what happens when both are full |
| `09` | Coordination Utilities | `CountDownLatchDemo`, `SemaphoreResourcePoolDemo`, brief tour of `CyclicBarrier`/`Exchanger`/`Phaser` | Each utility as a different kind of rendezvous point — one-shot gate, reusable meeting point, resource ticket counter, two-party swap |
| `10` | Futures & CompletableFuture | `CompletableFutureChainingDemo`, `FutureLimitationsDemo` | A pipeline of callbacks vs. a value you must sit and wait for |
| `11` | Fork/Join & Parallel Streams | `RecursiveTaskSumDemo`, `CommonPoolStarvationDemo` | Divide-and-conquer as recursive splitting until work is "small enough," and why blocking calls poison a shared pool |
| `12` | Lock-Free Structures | `LockFreeStackDemo`, `SpscRingBufferDemo` | Progress guaranteed for *some* thread on every step, traded for the mental overhead of retry loops |
| `13` | Deadlock/Livelock/Starvation | `DeadlockDemo` vs `DeadlockFixLockOrderingDemo`/`DeadlockFixTryLockBackoffDemo` | Circular wait as a cycle in a resource graph; the fix as breaking the cycle, not avoiding locks |
| `14` | Virtual Threads & Structured Concurrency | `VirtualThreadPinningDemo`, `ManualStructuredConcurrencyDemo` | Millions of cheap threads mapped onto few carriers, and pinning as the one place that mapping leaks |
| `15` | Reactive (Project Reactor) | `MonoFluxBasicsDemo`, `BackpressureDemo` | Pull-based streams — the consumer sets the pace, nobody blocks a thread waiting |
| `16` | Design Patterns | `TokenBucketRateLimiter`, `CircuitBreaker`, `BoundedConnectionPool`, `ThreadSafeLazySingletonDemo` | Each pattern as the standard answer to a recurring shape of problem — traffic shaping, cascading failure, bounded sharing, safe one-time init |
| `17` | Performance & Observability | `ThreadDumpAnalysisDemo`, `ContentionMonitoringDemo`, `JfrRecordingDemo` | You can't fix contention you can't see — thread dumps and JFR as the tools that make it visible |
| `18` | Capstone | Full walk-through of `MatchingEngine`/`OrderBook`/`SymbolEngine` | A real system as the sum of every prior module's decision, each one traceable to the problem it solves |
| `19` | Benchmarks | JMH methodology and a couple of representative results | Why intuition about "which is faster" is often wrong until measured correctly |

## Build steps

1. Create `docs/` and `docs/README.md` with the TOC and mental-model
   summaries stubbed in, so the whole shape is visible before writing
   full content.
2. For each module, read the existing README plus the actual demo source
   (not just its README description) — the explanation has to be
   verified against real code, not paraphrased from a summary of it.
3. For each topic, write the failure-first scenario, then the mental
   model, then the first-principles concept explanation, then trace the
   worked example(s) interleaving-by-interleaving.
4. Write the misconceptions section as belief → correction → concrete
   breaking input, drawing from but expanding on the source README's
   pitfalls.
5. Add the "where this shows up for real" grounding and any diagram that
   clarifies a sequence.
6. Write 2-4 check-yourself questions per file, then compile
   `99-retrieval-practice.md` from all of them, deliberately reordered.
7. Fill in the index's mental-model summaries and "how it all connects"
   reasoning last, once all 19 files exist.
8. Review every file for one failure mode above all others: a true
   statement that isn't actually explained (an unearned fact). If a
   sentence states a conclusion without the reasoning that makes it
   obvious, fix the explanation rather than trim it.
9. Link `docs/README.md` from the repo root `README.md`.

## Open choices to confirm before writing full content

- Whether `18-capstone...md` should re-derive every design decision in
  prose or lean on an expanded version of the capstone README's existing
  mapping table (recommended: expand the table with reasoning rather than
  write the whole capstone from scratch, since the table's mapping is
  itself the valuable, memorable artifact for "design a system" questions).
- Code language in examples: kept as real Java excerpts from the repo (not
  pseudocode) so every traced example is verifiable against real,
  compiling source rather than an idealized rewrite.
