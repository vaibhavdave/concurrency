# M07 — Producer/Consumer with Blocking Queues

## 🎯 Learning Objectives
- Build producer/consumer pipelines using `BlockingQueue` implementations instead of hand-rolled `wait`/`notify` or `Condition` buffers.
- Understand backpressure: a bounded queue makes a fast producer block instead of unboundedly growing memory.
- Understand the direct-handoff semantics of `SynchronousQueue` (capacity zero).
- Use `PriorityBlockingQueue` to process work in priority order independent of arrival order.
- Chain multiple `BlockingQueue`s into a multi-stage pipeline, each stage its own worker thread(s).

## 📖 Concept
`BlockingQueue` is the standard library's answer to "I need a thread-safe hand-off point between producer threads and consumer threads, with blocking `put`/`take` built in" — no manual locks, conditions, or wait loops required.

```
 producer thread(s)                 consumer thread(s)
        |                                   |
        v            BlockingQueue          v
   put(item)  ----> [ ][ ][ ][ ][ ] ----> take()
                     (bounded or unbounded)
```

Different implementations trade off capacity, ordering, and blocking behavior:

| Implementation | Capacity | Ordering | Notes |
|---|---|---|---|
| `ArrayBlockingQueue` | fixed bound | FIFO | classic bounded buffer; `put()` blocks when full (backpressure) |
| `LinkedBlockingQueue` | optionally bounded | FIFO | good default; unbounded by default unless you cap it |
| `SynchronousQueue` | **zero** | n/a | every `put()` waits for a matching `take()` — a pure handoff, never buffers |
| `PriorityBlockingQueue` | unbounded | by `Comparator`/`Comparable` | `take()` always returns the current highest-priority element, not the oldest |

A multi-stage pipeline just chains several of these together, with one or more worker threads reading from queue *N* and writing to queue *N+1*:

```mermaid
flowchart LR
    P[producer] -->|rawQueue| A[parse stage]
    A -->|parsedQueue| B[transform stage]
    B -->|sumsQueue| C[sink stage]
```

Each stage only knows about the queue it reads from and the queue it writes to — stages don't need direct references to each other, which is what makes this pattern easy to scale (add more worker threads per stage) or extend (insert a new stage in the middle).

## ⚠️ Common Pitfalls
- Using an unbounded `LinkedBlockingQueue` for a producer that can outrun its consumer "because it's simpler" — you've just traded a clear backpressure signal for unbounded memory growth and an eventual `OutOfMemoryError`.
- Forgetting that `SynchronousQueue.put()` blocks until a consumer is *already* (or concurrently) calling `take()` — it is easy to deadlock a demo by starting the producer before any consumer thread exists.
- Assuming a `PriorityBlockingQueue` is a good FIFO substitute — elements with equal priority have **no** guaranteed relative order, and starvation of low-priority items is possible under sustained high-priority load.
- Testing producer/consumer code with `Thread.sleep()` to "wait for the item to arrive" — this is exactly the kind of test flakiness `poll(timeout, unit)` exists to avoid; always block-with-timeout on the queue instead of guessing a sleep duration.
- Not having a shutdown/termination signal (e.g. a poison pill) for pipeline stages — worker threads that only ever call blocking `take()` will hang forever once producers stop.

## 🧪 Demos in This Module
| Class | What it demonstrates | Run command |
|---|---|---|
| `ArrayBlockingQueuePipelineDemo` | Bounded producer/consumer; `put()` blocks (backpressure) once the queue is full | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m07_producer_consumer_blocking_queues.ArrayBlockingQueuePipelineDemo` |
| `SynchronousQueueHandoffDemo` | Zero-capacity direct handoff, thread-per-task style | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m07_producer_consumer_blocking_queues.SynchronousQueueHandoffDemo` |
| `PriorityBlockingQueueDemo` | Tasks produced out of order, consumed in priority order | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m07_producer_consumer_blocking_queues.PriorityBlockingQueueDemo` |
| `MultiStageQueuePipelineDemo` | 3-stage pipeline (parse -> transform -> sink) chained via `BlockingQueue`s | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m07_producer_consumer_blocking_queues.MultiStageQueuePipelineDemo` |

## ▶️ How to Run
Run any demo's `main()` directly via `exec:java` (see the table) or from your IDE.

Run this module's tests only:
```
mvn -pl concurrency-lab test -Dtest=m07_producer_consumer_blocking_queues.**
```
or a single class:
```
mvn -pl concurrency-lab test -Dtest=BlockingQueuePipelineTest
```
Tests feed known input into the queues and read output back with `poll(timeout, unit)` rather than `Thread.sleep`, so they stay deterministic under load.

## 📊 Sample Output
```
== Bounded queue capacity=3 ==
[producer] put 1 (blocked 0ms, queue size=1/3)
[producer] put 2 (blocked 0ms, queue size=2/3)
[producer] put 3 (blocked 0ms, queue size=3/3)
[consumer] took 1
[producer] put 4 (blocked 47ms, queue size=3/3)
[consumer] took 2
...
```

## 🔗 Further Reading
- [`BlockingQueue` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingQueue.html)
- [`SynchronousQueue` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SynchronousQueue.html)
- Brian Goetz, *Java Concurrency in Practice*, Chapter 5.3 (Blocking Queues and the Producer-Consumer Pattern)
- Module `m17` (if present in this curriculum) for how `ExecutorService`/thread pools build on these same queueing ideas
