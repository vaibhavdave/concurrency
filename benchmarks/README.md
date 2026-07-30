# Benchmarks — JMH Micro-benchmarks

## 🎯 Purpose
Demos in `concurrency-lab` print informal `System.nanoTime()` timings — good for building intuition, bad for drawing real conclusions (JIT warm-up, GC pauses, and measurement overhead can easily swamp the effect you're trying to see). This module uses [JMH](https://github.com/openjdk/jmh) (Java Microbenchmark Harness) — the same tool the JDK team uses to benchmark the JDK itself — to get statistically defensible numbers for the comparisons this curriculum keeps making informally.

It's a separate Maven module (not a package inside `concurrency-lab`) because JMH's annotation processor and Spring Boot's repackaging step both want to control the build's `Main-Class`/executable-jar shape, and mixing them in one module is a well-known source of pain. `concurrency-lab` publishes its plain, unrepackaged jar (see its `spring-boot-maven-plugin` config using the `exec` classifier) so this module can depend on it normally.

## 📖 What's Benchmarked
| Benchmark class | Compares | Companion module |
|---|---|---|
| `CounterContentionBenchmark` | `synchronized` increment vs. `AtomicLong` vs. `LongAdder`, 8 contending threads | m02, m04 |
| `LockContentionBenchmark` | `synchronized` vs. `ReentrantLock` vs. `StampedLock` (write lock) guarding a 2-field update, 8 contending threads | m05 |
| `VirtualVsPlatformThreadBenchmark` | Wall-clock time to run 5,000 short blocking tasks on a bounded platform-thread pool vs. one virtual thread per task | m14 |
| `MatchingEngineThroughputBenchmark` | Sustained order-submission throughput against the capstone's actor-per-symbol `MatchingEngine`, 16 concurrent submitters | capstone |

## ⚠️ Reading JMH Results Correctly
- **Throughput mode** (`CounterContentionBenchmark`, `LockContentionBenchmark`, `MatchingEngineThroughputBenchmark`) reports operations/second — higher is better.
- **SingleShotTime mode** (`VirtualVsPlatformThreadBenchmark`) reports milliseconds for one full batch of 5,000 tasks — lower is better. This mode intentionally skips JMH's usual JIT-warm-up assumption, because "how long does this batch take" (not "steady-state ops/sec") is the realistic question for a bounded burst of blocking work.
- `@Threads(8)`/`@Threads(16)` on a benchmark method means JMH runs that many concurrent invocations per iteration — the numbers already reflect contention, don't re-multiply them.
- Absolute numbers are entirely machine-dependent (core count, whether you're on a shared/virtualized CPU, JDK build). Compare relative ordering and rough magnitude between benchmark methods in the same run, on the same machine, not absolute numbers against numbers you find elsewhere.
- Always run with at least one fork and a handful of warm-up iterations (already configured on each class) — a same-JVM run without warm-up mixes interpreted/C1/C2-compiled code into one measurement and is close to meaningless.

## ▶️ How to Run

Build the module (this also builds `concurrency-lab`, which it depends on):
```bash
mvn -pl benchmarks -am package -DskipTests
```

Run every benchmark:
```bash
java -jar benchmarks/target/benchmarks.jar
```

Run a subset by class-name regex, and override iteration counts for a quicker smoke test:
```bash
java -jar benchmarks/target/benchmarks.jar CounterContentionBenchmark -wi 1 -i 2 -f 1
```

List available benchmarks without running them:
```bash
java -jar benchmarks/target/benchmarks.jar -l
```

## 📊 Sample Output
```
Benchmark                                                          Mode  Cnt        Score        Error  Units
CounterContentionBenchmark.atomicIncrement                        thrpt    5  42311456.210 ± 913204.552  ops/s
CounterContentionBenchmark.longAdderIncrement                     thrpt    5  118539027.331 ± 2201983.774  ops/s
CounterContentionBenchmark.synchronizedIncrement                  thrpt    5  31876540.902 ± 754112.008  ops/s

VirtualVsPlatformThreadBenchmark.platformThreadsBlockingTasks        ss    5      612.402 ±     48.117  ms/op
VirtualVsPlatformThreadBenchmark.virtualThreadsBlockingTasks         ss    5       58.914 ±      6.230  ms/op
```
(Illustrative only — run it on your own machine for real numbers.)

## 🔗 Further Reading
- [JMH samples](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples) — the canonical "gotchas" tour (dead-code elimination, constant folding, false sharing).
- [Aleksey Shipilëv's JMH talks](https://shipilev.net/) — the JMH author's deep dives into what makes microbenchmarking hard.
