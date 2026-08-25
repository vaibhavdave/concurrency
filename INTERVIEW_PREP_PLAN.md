# Plan: One-Day Interview-Prep Documentation

## Goal

A single, self-contained document (`INTERVIEW_PREP.md` at repo root) that lets
someone revise **every concurrency topic in this repo in one day** — concepts
explained in prose, not all 100+ demo classes, just the one or two examples
per topic that carry the idea. Optimized for re-reading the night before an
interview, not for learning from scratch.

Source of truth: this repo already has a `README.md` per module
(`m01`…`m17`, capstone, benchmarks) with a consistent structure — `Learning
Objectives`, `Concept`, `Common Pitfalls`, `Demos`, `Sample Output`. That
structure is the raw material to condense, not something to re-derive from
code.

## Deliverable shape

One file: **`INTERVIEW_PREP.md`**, structured as:

1. **Quick-reference cheat sheet** (top of file) — one table: module →
   one-line "what problem it solves" → the one API/class to remember. This
   is the last-thing-you-read-before-walking-in section.
2. **Table of contents** linking to each module section.
3. **19 topic sections**, one per module + capstone + benchmarks (see table
   below), each following a fixed template (see "Per-topic template").
4. **Cross-cutting "how it all connects" section** — reuse/condense the
   capstone README's existing table mapping design decisions back to
   modules; this is gold for "walk me through a concurrent system you'd
   design" questions.
5. **Appendix: rapid-fire Q&A** — ~2-3 likely interview questions per module
   with terse model answers, for a final skim.

## Per-topic template (applied to each of the 19 sections)

- **One-paragraph overview** (2-4 sentences): the problem this topic solves
  and why the previous topic's solution wasn't enough — the repo's own
  narrative ("every module solves the problem the previous one wasn't quite
  good enough for") is the throughline to preserve.
- **Core concepts** (bullets, 4-6 max): condensed from each README's
  `Learning Objectives` + `Concept` sections.
- **1-2 example snippets**: not full files — 10-20 line excerpts from the
  single most illustrative demo class per topic (chosen list below),
  trimmed to the lines that show the mechanism, with a one-line caption of
  what to notice.
- **Pitfalls / interview traps** (bullets, 3-5 max): condensed from each
  README's `Common Pitfalls` section — this is exam-relevant, higher
  density than the concept section.
- **One diagram** only where the repo's existing Mermaid diagram earns its
  space (M02 wait/notify, M13 deadlock, M14 pinning, M15 backpressure,
  capstone architecture); skip diagrams elsewhere to keep sections short.

## Topic list, chosen canonical example(s), and length budget

| # | Module | Canonical example(s) to excerpt | Target length |
|---|--------|----------------------------------|---------------|
| 01 | Thread Fundamentals | `ThreadLifecycleDemo`, `ThreadJoinAndInterruptDemo` | ~150 words + 1 snippet |
| 02 | Race Conditions & `synchronized` | `UnsafeCounter` vs `SafeSynchronizedCounter`, `WaitNotifyBoundedBuffer` | ~200 words + 2 snippets |
| 03 | JMM & `volatile` | `VisibilityProblemDemo` vs `VolatileFixDemo` | ~180 words + 1 snippet |
| 04 | Atomics & CAS | `AtomicCounterDemo`, `AbaProblemDemo` | ~180 words + 1 snippet |
| 05 | Explicit Locks | `ReentrantLockBasicsDemo`, `ReadWriteLockDemo` | ~180 words + 1 snippet |
| 06 | Concurrent Collections | `ConcurrentHashMapDemo`, `CompoundActionPitfallDemo` | ~180 words + 1 snippet |
| 07 | Producer/Consumer Queues | `ArrayBlockingQueuePipelineDemo` | ~150 words + 1 snippet |
| 08 | Executors & Thread Pools | `ThreadPoolExecutorTuningDemo`, `GracefulShutdownDemo` | ~200 words + 1 snippet |
| 09 | Coordination Utilities | `CountDownLatchDemo`, `SemaphoreResourcePoolDemo` (mention Cyclic/Exchanger/Phaser briefly, no snippet) | ~220 words + 1 snippet |
| 10 | Futures & CompletableFuture | `CompletableFutureChainingDemo` | ~180 words + 1 snippet |
| 11 | Fork/Join & Parallel Streams | `RecursiveTaskSumDemo`, `CommonPoolStarvationDemo` | ~180 words + 1 snippet |
| 12 | Lock-Free Structures | `LockFreeStackDemo` | ~180 words + 1 snippet |
| 13 | Deadlock/Livelock/Starvation | `DeadlockDemo` vs `DeadlockFixLockOrderingDemo` | ~200 words + 1 snippet + diagram |
| 14 | Virtual Threads & Structured Concurrency | `VirtualThreadPinningDemo`, `ManualStructuredConcurrencyDemo` | ~200 words + 1 snippet + diagram |
| 15 | Reactive (Project Reactor) | `MonoFluxBasicsDemo`, `BackpressureDemo` | ~200 words + 1 snippet + diagram |
| 16 | Design Patterns | `TokenBucketRateLimiter`, `CircuitBreaker` (mention pool/singleton/actor briefly) | ~220 words + 1 snippet |
| 17 | Performance & Observability | `ThreadDumpAnalysisDemo`, `ContentionMonitoringDemo` | ~150 words + 1 snippet |
| 🏆 | Capstone | Condensed version of its own "ties curriculum together" table | ~250 words, table reused |
| 📊 | Benchmarks | Summary of what's measured + how to read JMH output correctly | ~150 words, no code |

Total budget: roughly **3,500-4,000 words of prose + ~20 short snippets**,
which prints to ~12-15 pages — a realistic single-day re-read, not a
re-learn.

## Build steps

1. For each module, pull `Learning Objectives`, `Concept`, and `Common
   Pitfalls` from its existing README — condense, don't rewrite from
   scratch (the source material is already accurate and reviewed).
2. Open the canonical demo file(s) listed above and extract the smallest
   excerpt that shows the mechanism (the race, the fix, the API call) —
   verify the excerpt still compiles/reads sensibly out of context.
3. Write the one-paragraph overview per topic, explicitly naming what
   problem the *previous* topic left unsolved (preserves the repo's
   narrative arc, which is itself a good interview answer structure).
4. Draft 2-3 rapid-fire Q&A per module for the appendix (e.g., "Why does
   `while`, not `if`, guard a `wait()` call?").
5. Assemble the quick-reference table and table of contents last, once
   section headings are final.
6. Proofread for length budget per section; cut before adding — if a
   section runs long, trim pitfalls/snippets rather than the overview.
7. Commit `INTERVIEW_PREP.md` at repo root, linked from the main
   `README.md`.

## Open choices to confirm before writing full content

- Single file vs. one condensed file per module (single file recommended —
  matches "revise in a day" and avoids link-hopping).
- Whether to include the capstone's full pitfalls list or just its
  cross-reference table (recommended: table only, pitfalls are already
  covered per-module).
- Code language in snippets: kept as real Java excerpts from the repo
  (not pseudocode), so they stay copy-paste-verifiable against source.
