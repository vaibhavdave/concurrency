← [18. Capstone: Order Matching Engine](18-capstone-order-matching-engine.md) | **19. Benchmarks** | Next → [Retrieval Practice](99-retrieval-practice.md)

# 19 — Benchmarks: Measuring What You Think You Know

## The failure, first

Every earlier module in this curriculum backed up a claim with a
`System.nanoTime()` measurement wrapped around a demo — "lock-free was
faster," "virtual threads finished in 118ms vs. 2.5 seconds." Those numbers
are genuinely useful for building intuition, and genuinely **not**
trustworthy as an actual performance claim, for a reason worth
internalizing on its own: a single untuned run mixes interpreted bytecode,
partially-JIT-compiled code, and fully-optimized code into one measurement,
plus whatever the garbage collector decided to do in the middle of it. Two
people running the "same" naive benchmark on the same machine can get
numbers that disagree by 2x depending purely on whether the JVM had warmed
up. This is the exact trap performance engineering has a name for:
measuring confidently, concluding wrongly. JMH (Java Microbenchmark
Harness) — the tool the JDK team itself uses to benchmark the JDK — exists
specifically to make that trap harder to fall into.

## Mental model: stop asking "how fast," start asking "how fast, measured how"

A `System.nanoTime()` timing wrapped around a `for` loop in a `main()`
method answers a question nobody actually asked: "how long did this one,
specific, cold-or-lukewarm run take, on this one occasion." JMH forces you
to be explicit about the question you actually want answered — steady-state
throughput after warm-up ("once the JIT has fully optimized this, how many
operations per second can it sustain?") or the realistic cost of one
bounded batch ("how long does this specific burst of work take, warm-up
assumptions aside?") — and structures the measurement so the answer isn't
contaminated by JIT compilation tiers, dead-code elimination, or constant
folding silently optimizing away the very thing you meant to measure.

## Concept, from first principles

### What's actually being re-measured, properly, and why each pairing was chosen

| Benchmark | Re-measures the claim from | Why JMH matters here |
|---|---|---|
| `CounterContentionBenchmark` | Module 02 (`synchronized`) vs. module 04 (`AtomicLong`, `LongAdder`) | These are exactly the kind of nanosecond-scale operations where JIT warm-up state completely changes the answer — an interpreted `synchronized` increment and a JIT-compiled one are not remotely comparable. |
| `LockContentionBenchmark` | Module 05 (`synchronized` vs. `ReentrantLock` vs. `StampedLock`) | Lock acquisition cost is small enough per-call that measurement overhead itself can swamp the real signal without a harness designed to subtract it out. |
| `VirtualVsPlatformThreadBenchmark` | Module 14 (virtual vs. platform threads) | Deliberately run in `SingleShotTime` mode, *not* throughput mode — because "how long does one realistic burst of 5,000 blocking tasks take" is the actual production question, and JMH's usual warm-up assumption would misrepresent a workload that's inherently a one-shot batch, not a steady-state loop. |
| `MatchingEngineThroughputBenchmark` | The capstone (module 18), under 16 concurrent submitters | Validates the whole actor-per-symbol design's real, sustained throughput claim with the same statistical rigor as the smaller, single-mechanism benchmarks — the capstone's own demo output is illustrative, this is the version you'd actually trust. |

### Reading the output without fooling yourself

A JMH report gives you a `Score` and an `Error` (margin) per benchmark
method — the error term is not decoration; a difference smaller than the
combined error bars of two methods is not a real difference, it's noise.
Two further rules matter more than the numbers themselves: `@Threads(8)`
on a benchmark method means the reported score **already reflects**
8-way contention — multiplying or dividing it further to "normalize" it
produces a nonsense number. And absolute scores are tied to the exact
machine, core count, and JDK build they were measured on; the only
meaningful comparison is *relative* — which of several methods was faster,
and by roughly how much — in the **same run, on the same machine**, never
against a number pasted from a blog post or this README's own illustrative
sample output.

## Misconceptions worth naming directly

- **Belief: "The informal `System.nanoTime()` numbers in the earlier
  modules' demos are basically as trustworthy as a real benchmark, just
  less polished."**
  Wrong — a single, unwarmed run conflates interpreted execution, partial
  JIT compilation, and full optimization into one number, and can also be
  skewed by an incidental GC pause; the demos are correct for building
  intuition about *direction* (which approach wins) but not for trusting
  the *magnitude*.

- **Belief: "Since `VirtualVsPlatformThreadBenchmark` uses JMH like the
  others, it must be reporting steady-state throughput too."**
  Wrong — it deliberately runs in `SingleShotTime` mode, skipping JMH's
  usual warm-up assumption entirely, because the real-world question for a
  bounded burst of blocking work is "how long does this one batch take,"
  not "what's the steady-state ops/sec after the JIT has fully optimized
  it" — those are different questions with different correct measurement
  approaches.

- **Belief: "A benchmark reporting `42,311,456 ± 913,204 ops/s` on my
  machine should be directly comparable to `31,876,540 ± 754,112 ops/s`
  someone else measured on theirs."**
  Wrong — absolute throughput numbers are entirely tied to the specific
  machine, core count, and JDK build they were measured on; only relative
  comparisons within the same run, same machine, are meaningful.

## Where this shows up for real

This is exactly the discipline behind any real capacity-planning or
performance-regression decision in production software: before concluding
"switch X to Y because it's faster," a credible engineering team backs
that claim with a properly warmed-up, statistically-bounded benchmark, not
a single stopwatch run — precisely the gap between this curriculum's
demo-level intuition-building and this module's actual, defensible
numbers. JMH itself (or a similar harness) is standard practice for any
JVM library or framework making a public performance claim.

## Check yourself

1. Why can a single, unwarmed `System.nanoTime()`-based timing produce a
   misleading result, even when the code being measured is completely
   correct?
2. Why does `VirtualVsPlatformThreadBenchmark` run in `SingleShotTime` mode
   instead of the throughput mode used by the other benchmarks in this
   module?
3. A benchmark method annotated `@Threads(8)` reports a score of 40 million
   ops/sec. Is it valid to divide that by 8 to get a "per-thread" number
   and compare it to a single-threaded benchmark's score? Why or why not?
4. Why is it invalid to compare this module's illustrative sample output
   numbers directly against numbers you measure on your own machine?

---

<details>
<summary>Answers</summary>

1. Because a single run can mix interpreted bytecode execution, partially
   JIT-compiled code, and fully optimized code together in one
   measurement, and can also be skewed by an incidental garbage collection
   pause landing inside the timed window — none of which reflects the
   code's actual steady-state performance.
2. Because the realistic question for a bounded burst of blocking work
   (5,000 tasks, run once) is "how long does this one batch take," not
   "what's the steady-state throughput after full JIT warm-up" — the usual
   throughput-mode warm-up assumption would misrepresent a workload that's
   inherently a single batch rather than a repeating steady-state loop.
3. No — the reported score already reflects the contention and scheduling
   behavior of 8 concurrent invocations; dividing it by 8 doesn't recover
   a meaningful "per-thread, uncontended" number, since contention effects
   are not linear or evenly distributed across threads.
4. Because absolute throughput and timing numbers are entirely dependent
   on the specific machine, core count, CPU architecture, and JDK build
   they were measured on — only relative comparisons made within the same
   run, on the same machine, are meaningful; comparing across different
   hardware or JVM versions compares noise, not signal.

</details>

---

← [18. Capstone: Order Matching Engine](18-capstone-order-matching-engine.md) | Next → [Retrieval Practice](99-retrieval-practice.md)
