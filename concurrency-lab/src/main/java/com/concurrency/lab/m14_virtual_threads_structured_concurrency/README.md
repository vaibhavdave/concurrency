# M14 — Virtual Threads & Structured Concurrency

## 🎯 Learning Objectives
- Understand the difference between platform threads (1:1 with an OS thread) and virtual threads (M:N, scheduled by the JVM onto a small pool of carrier threads).
- See why virtual threads deliver dramatically higher throughput for blocking, I/O-bound workloads.
- Recognize the "carrier pinning" hazard caused by blocking inside a `synchronized` block, and why `ReentrantLock` avoids it.
- Learn the manual, pre-preview way to approximate structured concurrency with a plain `ExecutorService`, and understand what the real `StructuredTaskScope` API (JEP 480) offers on top of it.
- Know the Spring Boot switch (`spring.threads.virtual.enabled=true`) that moves the embedded Tomcat request-handling threads onto virtual threads.

## 📖 Concept

A **platform thread** is a thin wrapper around an OS thread: creating one costs roughly a megabyte of stack and a kernel-level context switch to schedule it. A **virtual thread** is a lightweight, JVM-managed thread: creation is cheap (a few hundred bytes), and the JVM multiplexes many virtual threads onto a much smaller pool of **carrier** platform threads (by default, `ForkJoinPool.commonPool()`, sized to the number of CPU cores).

The key trick is what happens when a virtual thread blocks on something the JVM understands (`Thread.sleep`, blocking I/O via `java.net`/NIO, `java.util.concurrent` locks, etc.): instead of the carrier thread sitting idle waiting, the virtual thread is **unmounted** from its carrier, and the carrier is freed to run a different virtual thread. When the blocking operation completes, the virtual thread is **remounted** onto any available carrier and resumes.

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

This means a pool of, say, 8 carrier threads can service tens of thousands of concurrently-blocked virtual threads, because at any instant only the virtual threads that are *actually running CPU work* occupy a carrier — the rest are parked cheaply in the heap, not tying up an OS thread.

**Pinning** is the exception to unmounting: if a virtual thread blocks while holding a monitor entered via `synchronized`, or while executing a native method / foreign function call, the JVM cannot safely unmount it, so the carrier stays occupied ("pinned") for the duration of the blocking call. `java.util.concurrent.locks.ReentrantLock` and friends are implemented to cooperate with the scheduler and do *not* pin.

**Structured concurrency** is the principle that a group of concurrently-forked subtasks should have a single, well-defined lifetime tied to a lexical scope: if the scope exits, all its subtasks are done (succeeded, failed, or were cancelled) — no subtask can outlive the block that spawned it, and errors propagate predictably to the parent. `try-with-resources` around an `ExecutorService` (which implements `AutoCloseable` since Java 19) gives a manual approximation of this; JEP 480's `StructuredTaskScope` (still preview in JDK 21+) formalizes it with built-in join policies and failure propagation.

## ⚠️ Common Pitfalls
- Using virtual threads for **CPU-bound** work: they don't add parallelism, only cheap concurrency for blocking workloads — CPU-bound tasks still contend for the same number of cores.
- Blocking inside `synchronized` on a hot path executed by virtual threads: this pins the carrier and can starve the whole application under load, since the default carrier pool is small (core count).
- Pooling virtual threads (e.g. wrapping them in a fixed-size pool): defeats the purpose — virtual threads are meant to be created per task, not reused/pooled.
- Forgetting to close the `ExecutorService` (or not using try-with-resources): `close()` on a virtual-thread-per-task executor waits for submitted tasks and prevents resource leaks.
- Treating the manual `ExecutorService`-based "structured concurrency" as equivalent to `StructuredTaskScope`: the manual version does not automatically cancel siblings the instant one fails (only after the first `future.get()` that observes it), and lacks scoped-value propagation.

## 🧪 Demos in This Module
| Class | What it demonstrates | Run command |
|---|---|---|
| `PlatformThreadsThroughputDemo` | Baseline: 10,000 blocking tasks on a fixed pool of 200 platform threads | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m14_virtual_threads_structured_concurrency.PlatformThreadsThroughputDemo` |
| `VirtualThreadsThroughputDemo` | Same 10,000 blocking tasks, one virtual thread per task | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m14_virtual_threads_structured_concurrency.VirtualThreadsThroughputDemo` |
| `VirtualThreadPinningDemo` | `synchronized` pinning the carrier vs `ReentrantLock` not pinning | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m14_virtual_threads_structured_concurrency.VirtualThreadPinningDemo` |
| `ManualStructuredConcurrencyDemo` | Fork/join subtasks with a try-with-resources `ExecutorService`, propagating the first failure and cancelling the rest | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m14_virtual_threads_structured_concurrency.ManualStructuredConcurrencyDemo` |

For the REST demo, run: `mvn -pl concurrency-lab spring-boot:run` then `curl localhost:8080/api/virtual-threads/whoami`.

By default the app runs on the Servlet stack with `spring.threads.virtual.enabled=false` in `application.yml` (a value this module's demos do not modify), so `whoami` will report a platform thread name (e.g. `http-nio-8080-exec-1`) and `virtual: false`. Flipping that property to `true` (locally, without editing the tracked file — e.g. via `-Dspring.threads.virtual.enabled=true`) switches Tomcat's request-handling threads to virtual threads, and the same endpoint will report a name like `virtual-...` / `tomcat-handler-...` and `virtual: true`.

### The real `StructuredTaskScope` (JEP 480, preview in JDK 21+)

This module does **not** compile against `StructuredTaskScope` because it is a preview API — enabling it would require `--enable-preview` for the *entire* build (`concurrency-lab` module), which we are not allowed to change (`pom.xml` is off-limits). The snippet below is documentation only, not compiled:

```java
// Requires --enable-preview on javac/java, JDK 21+ (finalized later as JEP evolves)
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> user = scope.fork(() -> fetchUser(userId));
    Subtask<Integer> orderCount = scope.fork(() -> fetchOrderCount(userId));

    scope.join();           // wait for both, or until one fails
    scope.throwIfFailed();  // propagate the first failure, others already cancelled

    return new UserSummary(user.get(), orderCount.get());
} // scope.close() guarantees no subtask leaks past this point
```

Compared to our manual `ExecutorService` version, `StructuredTaskScope`:
- Cancels sibling subtasks **as soon as** one fails (`ShutdownOnFailure`), not just when the caller happens to call `get()` on the failed future.
- Offers alternative join policies, e.g. `ShutdownOnSuccess` (race semantics: first success wins).
- Propagates `ScopedValue` context to forked subtasks cleanly.
- Makes the "no thread outlives its scope" invariant structural rather than a matter of discipline.

## ▶️ How to Run
```bash
mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m14_virtual_threads_structured_concurrency.PlatformThreadsThroughputDemo
mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m14_virtual_threads_structured_concurrency.VirtualThreadsThroughputDemo
mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m14_virtual_threads_structured_concurrency.VirtualThreadPinningDemo -Djdk.tracePinnedThreads=full
mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m14_virtual_threads_structured_concurrency.ManualStructuredConcurrencyDemo
mvn -pl concurrency-lab test -Dtest=com.concurrency.lab.m14_virtual_threads_structured_concurrency.*
```

To see pinning diagnostics in `VirtualThreadPinningDemo`, pass `-Djdk.tracePinnedThreads=full` (or `=short`) as shown above — the JVM prints a stack trace every time a virtual thread parks while pinned. You can also capture `jdk.VirtualThreadPinned` JFR events (`jcmd <pid> JFR.start settings=profile`, then inspect with `jfr print --events jdk.VirtualThreadPinned recording.jfr`) instead of, or in addition to, the tracing flag.

## 📊 Sample Output
Illustrative only — actual numbers are highly machine-dependent (CPU count, OS scheduler, JVM version):
```
== Platform-thread pool throughput ==
Submitting 10000 blocking tasks to a fixed pool of 200 platform threads
Completed 10000 tasks in 2537 ms

== Virtual-thread-per-task throughput ==
Submitting 10000 blocking tasks, one virtual thread each
Completed 10000 tasks in 118 ms
```

## 🔗 Further Reading
- JEP 444: Virtual Threads — https://openjdk.org/jeps/444
- JEP 480: Structured Concurrency (Third Preview) — https://openjdk.org/jeps/480
- Oracle Java Platform, Standard Edition Virtual Threads Guide — https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
- Spring Boot "Embracing Virtual Threads" reference docs — https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html
