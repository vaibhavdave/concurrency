# Plan: One-Day Interview-Prep Documentation

## Goal

A set of documents under `docs/` that let someone revise **every concurrency
topic in this repo in one day** — concepts explained in prose, not all 100+
demo classes, just the one or two examples per topic that carry the idea.
Optimized for re-reading the night before an interview, not for learning from
scratch.

Source of truth: this repo already has a `README.md` per module
(`m01`…`m17`, capstone, benchmarks) with a consistent structure — `Learning
Objectives`, `Concept`, `Common Pitfalls`, `Demos`, `Sample Output`. That
structure is the raw material to condense, not something to re-derive from
code.

## Deliverable shape

**`docs/` folder, one file per topic, plus an index:**

```
docs/
├── README.md                                          # index: cheat sheet + TOC + "how it connects"
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
└── 99-rapid-fire-qa.md                                 # cross-module Q&A appendix
```

Numbered filenames (matching the existing module numbers) keep the folder
sorted in reading order in any file browser, without depending on the index
page. Names otherwise mirror the existing module folder names for easy
cross-reference back to source.

### `docs/README.md` (the index page)

This is the single entry point for a one-day revision pass:

1. **Quick-reference cheat sheet** — one table: module → link to its file →
   one-line "what problem it solves" → the one API/class to remember. Skimmable
   in under 5 minutes; the last-thing-you-read-before-walking-in section.
2. **Table of contents** — links to all 19 topic files in order, plus the
   Q&A appendix.
3. **"How it all connects"** — condensed version of the capstone README's
   existing table mapping design decisions back to modules; good material
   for "walk me through a concurrent system you'd design" questions.
4. A one-line pointer to `99-rapid-fire-qa.md` for a final skim.

### Each numbered topic file (`01`…`19`), fixed template

- **One-paragraph overview** (2-4 sentences): the problem this topic solves
  and why the previous topic's solution wasn't enough — the repo's own
  narrative ("every module solves the problem the previous one wasn't quite
  good enough for") is the throughline to preserve, and it still works
  file-to-file since each links to the previous/next at the top.
- **Core concepts** (bullets, 4-6 max): condensed from the source README's
  `Learning Objectives` + `Concept` sections.
- **1-2 example snippets**: not full files — 10-20 line excerpts from the
  single most illustrative demo class per topic (chosen list below),
  trimmed to the lines that show the mechanism, with a one-line caption of
  what to notice and a path back to the full source file.
- **Pitfalls / interview traps** (bullets, 3-5 max): condensed from the
  source README's `Common Pitfalls` section — exam-relevant, higher density
  than the concept section.
- **One diagram** only where the repo's existing Mermaid diagram earns its
  space (M02 wait/notify, M13 deadlock, M14 pinning, M15 backpressure,
  capstone architecture); skip diagrams elsewhere to keep files short.
- **Prev / Next** links at top and bottom, so the set can still be read
  start-to-finish like a single document.

### `99-rapid-fire-qa.md`

~2-3 likely interview questions per module with terse model answers, grouped
under a heading per module, for a final skim — kept separate from the topic
files so it doesn't bloat them, but each topic file links down to its own
slice of this file.

## Topic list, chosen canonical example(s), and length budget

| File | Module | Canonical example(s) to excerpt | Target length |
|---|--------|----------------------------------|---------------|
| `01` | Thread Fundamentals | `ThreadLifecycleDemo`, `ThreadJoinAndInterruptDemo` | ~150 words + 1 snippet |
| `02` | Race Conditions & `synchronized` | `UnsafeCounter` vs `SafeSynchronizedCounter`, `WaitNotifyBoundedBuffer` | ~200 words + 2 snippets |
| `03` | JMM & `volatile` | `VisibilityProblemDemo` vs `VolatileFixDemo` | ~180 words + 1 snippet |
| `04` | Atomics & CAS | `AtomicCounterDemo`, `AbaProblemDemo` | ~180 words + 1 snippet |
| `05` | Explicit Locks | `ReentrantLockBasicsDemo`, `ReadWriteLockDemo` | ~180 words + 1 snippet |
| `06` | Concurrent Collections | `ConcurrentHashMapDemo`, `CompoundActionPitfallDemo` | ~180 words + 1 snippet |
| `07` | Producer/Consumer Queues | `ArrayBlockingQueuePipelineDemo` | ~150 words + 1 snippet |
| `08` | Executors & Thread Pools | `ThreadPoolExecutorTuningDemo`, `GracefulShutdownDemo` | ~200 words + 1 snippet |
| `09` | Coordination Utilities | `CountDownLatchDemo`, `SemaphoreResourcePoolDemo` (mention Cyclic/Exchanger/Phaser briefly, no snippet) | ~220 words + 1 snippet |
| `10` | Futures & CompletableFuture | `CompletableFutureChainingDemo` | ~180 words + 1 snippet |
| `11` | Fork/Join & Parallel Streams | `RecursiveTaskSumDemo`, `CommonPoolStarvationDemo` | ~180 words + 1 snippet |
| `12` | Lock-Free Structures | `LockFreeStackDemo` | ~180 words + 1 snippet |
| `13` | Deadlock/Livelock/Starvation | `DeadlockDemo` vs `DeadlockFixLockOrderingDemo` | ~200 words + 1 snippet + diagram |
| `14` | Virtual Threads & Structured Concurrency | `VirtualThreadPinningDemo`, `ManualStructuredConcurrencyDemo` | ~200 words + 1 snippet + diagram |
| `15` | Reactive (Project Reactor) | `MonoFluxBasicsDemo`, `BackpressureDemo` | ~200 words + 1 snippet + diagram |
| `16` | Design Patterns | `TokenBucketRateLimiter`, `CircuitBreaker` (mention pool/singleton/actor briefly) | ~220 words + 1 snippet |
| `17` | Performance & Observability | `ThreadDumpAnalysisDemo`, `ContentionMonitoringDemo` | ~150 words + 1 snippet |
| `18` | Capstone | Condensed version of its own "ties curriculum together" table | ~250 words, table reused |
| `19` | Benchmarks | Summary of what's measured + how to read JMH output correctly | ~150 words, no code |

Total budget: roughly **3,500-4,000 words of prose across 19 files + ~20
short snippets**, plus the index and Q&A appendix — the same total reading
material as before, just split so each topic is a self-contained,
independently linkable file instead of one long scroll.

## Build steps

1. Create `docs/` and `docs/README.md` with the TOC skeleton (links to files
   that don't exist yet) so structure is locked in before content.
2. For each module, pull `Learning Objectives`, `Concept`, and `Common
   Pitfalls` from its existing README — condense, don't rewrite from
   scratch (the source material is already accurate and reviewed).
3. Open the canonical demo file(s) listed above and extract the smallest
   excerpt that shows the mechanism (the race, the fix, the API call) —
   verify the excerpt still compiles/reads sensibly out of context, and
   link back to the real file path.
4. Write the one-paragraph overview per topic, explicitly naming what
   problem the *previous* topic left unsolved, and add prev/next links.
5. Write `99-rapid-fire-qa.md` with 2-3 Q&A per module, and link each
   topic file down to its slice.
6. Fill in the index's quick-reference table and "how it all connects"
   section last, once all 19 files and their headings are final.
7. Proofread each file against its length budget; cut before adding — if a
   file runs long, trim pitfalls/snippets rather than the overview.
8. Link `docs/README.md` from the repo root `README.md`.

## Open choices to confirm before writing full content

- Whether to keep the capstone's full pitfalls list in `18-capstone...md`
  or just its cross-reference table (recommended: table only, pitfalls are
  already covered per-module).
- Code language in snippets: kept as real Java excerpts from the repo
  (not pseudocode), so they stay copy-paste-verifiable against source.
