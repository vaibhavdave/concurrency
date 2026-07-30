# M08 — Executors and Thread Pools

## 🎯 Learning Objectives
- Choose the right `Executors` factory (fixed, cached, single-thread, scheduled) for a given workload shape
- Build and tune a raw `ThreadPoolExecutor` with explicit core/max size, keep-alive, and a bounded queue
- Understand what happens to work when a pool is saturated, and how each `RejectedExecutionHandler` differs
- Shut down an `ExecutorService` correctly without leaking threads or dropping in-flight work
- Distinguish `scheduleAtFixedRate` from `scheduleWithFixedDelay` when a task occasionally overruns its period
- Expose a thread pool's live behavior over HTTP so you can drive load against a running Spring Boot app

## 📖 Concept
A thread pool decouples "submitting work" from "running work." Callers hand a `Runnable`/`Callable` to an
`Executor`; a bounded set of worker threads pulls from an internal queue and executes tasks, reusing threads
instead of paying thread-creation cost per task.

```
submit(task) -> [ BlockingQueue ] -> [ worker-1 ][ worker-2 ]...[ worker-N ]
                      ^                                              |
                      |-- if pool at max size AND queue full --------|
                      v
              RejectedExecutionHandler (Abort / CallerRuns / Discard / DiscardOldest)
```

`ThreadPoolExecutor` admission order is important and often misunderstood:
1. If fewer than `corePoolSize` threads exist, start a new thread for the task (even if others are idle).
2. Otherwise, try to enqueue the task in the `BlockingQueue`.
3. If the queue is full and fewer than `maximumPoolSize` threads exist, start a new thread.
4. If the queue is full and the pool is already at `maximumPoolSize`, hand the task to the
   `RejectedExecutionHandler`.

Idle threads above `corePoolSize` are reclaimed after `keepAliveTime`. `Executors.newFixedThreadPool` and
`newSingleThreadExecutor` use an unbounded `LinkedBlockingQueue`, which is why step 3/4 never trigger for
them — tasks simply queue forever instead of being rejected (a common source of unbounded memory growth).
`newCachedThreadPool` uses a `SynchronousQueue` with `maximumPoolSize = Integer.MAX_VALUE`, so it grows a
thread per task instead of queuing.

`scheduleAtFixedRate` anchors every run to `initialDelay + n * period`, so if one run overruns the period the
*next* run fires immediately to catch up (runs can back-to-back but never drift the schedule). `scheduleWithFixedDelay`
instead waits `delay` after each run *finishes*, so an overrun permanently shifts every later run later.

## ⚠️ Common Pitfalls
- Using `Executors.newFixedThreadPool`/`newCachedThreadPool` in production without a bounded queue — unbounded
  queues hide backpressure problems until an `OutOfMemoryError`
- Forgetting that `newCachedThreadPool` has no upper bound on thread count — a burst of slow tasks can create
  thousands of threads
- Choosing `AbortPolicy` (the default) without a catch for `RejectedExecutionException`, crashing the caller
- Assuming `shutdown()` stops running tasks — it only stops *new* submissions; use `shutdownNow()` to interrupt
  in-flight work
- Not calling `awaitTermination` after `shutdown()`/`shutdownNow()`, so the JVM exits (or the caller proceeds)
  before workers actually finish
- Confusing `scheduleAtFixedRate` with a guarantee of non-overlapping runs — a single-threaded scheduler will
  never run two instances concurrently, but a multi-threaded one can

## 🧪 Demos in This Module
| Class | What it demonstrates | Run command |
|---|---|---|
| `ExecutorTypesDemo` | Behavioral differences between fixed, cached, single-thread, and scheduled pools | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m08_executors_and_thread_pools.ExecutorTypesDemo` |
| `ThreadPoolExecutorTuningDemo` | Raw `ThreadPoolExecutor` tuning and all 4 `RejectedExecutionHandler`s under overload | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m08_executors_and_thread_pools.ThreadPoolExecutorTuningDemo` |
| `GracefulShutdownDemo` | `shutdown()` vs `shutdownNow()` vs `awaitTermination`, and the correct shutdown sequence | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m08_executors_and_thread_pools.GracefulShutdownDemo` |
| `ScheduledTaskDemo` | `scheduleAtFixedRate` vs `scheduleWithFixedDelay` when a task runs long | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m08_executors_and_thread_pools.ScheduledTaskDemo` |

### REST demo: live load against a running Spring Boot app
`ExecutorDemoController` exposes a configurable `ThreadPoolExecutor` at `POST /api/executors/simulate`. Start
the app, then fire requests at it to watch submitted/completed/rejected counts change with pool size and load:

```bash
mvn -pl concurrency-lab spring-boot:run
```

```bash
curl -X POST "localhost:8080/api/executors/simulate?tasks=200&poolSize=8&workMillis=50"
```

Try a burst larger than `poolSize * 2` (the queue capacity) to see `rejected` become non-zero:

```bash
curl -X POST "localhost:8080/api/executors/simulate?tasks=500&poolSize=4&workMillis=200"
```

## ▶️ How to Run
Each demo class has a `main` method and can be run directly:
```bash
mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m08_executors_and_thread_pools.<ClassName>
```
Or run the tests for this module only:
```bash
mvn -pl concurrency-lab test -Dtest="com.concurrency.lab.m08_executors_and_thread_pools.*"
```

## 📊 Sample Output
```
== AbortPolicy ==
[AbortPolicy] completed task 0 on pool-1-thread-1
[AbortPolicy] completed task 1 on pool-1-thread-2
[AbortPolicy] task 6 rejected: Task ... rejected from java.util.concurrent.ThreadPoolExecutor@...
[AbortPolicy] task 7 rejected: Task ... rejected from java.util.concurrent.ThreadPoolExecutor@...
[AbortPolicy] completed=4 explicitlyRejected=4

== CallerRunsPolicy ==
[CallerRunsPolicy] completed task 5 on main
[CallerRunsPolicy] completed=8 explicitlyRejected=0
```

```json
{"submitted":200,"completed":200,"rejected":0,"elapsedMillis":1310,"activeCount":0,"queueSize":0}
```

## 🔗 Further Reading
- [`ThreadPoolExecutor` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [`Executors` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html)
- Brian Goetz, *Java Concurrency in Practice*, Chapter 8 — Applying Thread Pools
