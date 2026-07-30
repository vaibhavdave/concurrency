# M09 — Coordination Utilities

## 🎯 Learning Objectives
- Use `CountDownLatch` for one-shot "wait until N things happen" signaling (startup and completion patterns)
- Use `CyclicBarrier` to synchronize a fixed group of threads at the end of each of several repeated rounds
- Bound concurrent access to a limited resource pool with `Semaphore`, including fair vs unfair acquisition
- Hand data back and forth between exactly two threads with `Exchanger`
- Coordinate a dynamically changing number of parties across multiple phases with `Phaser`
- Recognize which utility fits which shape of coordination problem

## 📖 Concept
These `java.util.concurrent` classes solve "make threads wait for each other," but differ in *reusability* and
*party count flexibility*:

| Utility | Reusable? | Party count | Typical use |
|---|---|---|---|
| `CountDownLatch` | No (one-shot) | Fixed at construction | Wait for N events, then never again |
| `CyclicBarrier` | Yes (resets automatically) | Fixed at construction | N threads sync at the same point, repeatedly |
| `Semaphore` | Yes | N/A (permits, not threads) | Bound concurrent access to a resource |
| `Exchanger` | Yes | Exactly 2 | Swap buffers/state between two threads |
| `Phaser` | Yes | Dynamic (register/deregister) | Multi-phase work where the party count changes |

```
CountDownLatch(3)      CyclicBarrier(3, action)         Phaser
  await() <---- 3x        round 1: 3x await() -> action    phase 0: parties {A,B,C}
  countDown()             round 2: 3x await() -> action        arriveAndDeregister (A leaves)
  (never resets)          ... reusable indefinitely       phase 1: parties {B,C,D} (D joined)
```

`Phaser` is the only one of these that lets parties register or deregister *between* phases — a `CyclicBarrier`'s
party count is fixed for its lifetime, and a `CountDownLatch` cannot be reset at all once it hits zero.

## ⚠️ Common Pitfalls
- Reusing a `CountDownLatch` expecting it to reset after reaching zero — it cannot; construct a new one per round
- Forgetting that a `CyclicBarrier` action runs on whichever thread happens to trip the barrier last, not on a
  dedicated "coordinator" thread
- Letting a `BrokenBarrierException` go unhandled — one thread timing out or being interrupted at the barrier
  breaks it for *all* other waiting threads
- Leaving a `Phaser` party permanently registered after it stops participating — the phase can never advance
  because the phaser keeps waiting for that party's arrival
- Assuming `Semaphore` provides mutual exclusion of *code* the way a lock does — it only limits the *count* of
  concurrent permit holders; it does not track which thread holds which permit
- Deadlocking an `Exchanger` because one side never calls `exchange()` — both sides must rendezvous every round

## 🧪 Demos in This Module
| Class | What it demonstrates | Run command |
|---|---|---|
| `CountDownLatchDemo` | Startup-readiness latch and completion-wait latch patterns | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m09_coordination_utilities.CountDownLatchDemo` |
| `CyclicBarrierDemo` | Threads syncing at a barrier every round, with a per-round aggregate barrier action | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m09_coordination_utilities.CyclicBarrierDemo` |
| `SemaphoreResourcePoolDemo` | Bounded simulated connection pool with more clients than permits | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m09_coordination_utilities.SemaphoreResourcePoolDemo` |
| `ExchangerDemo` | Producer/consumer repeatedly swapping buffers via `Exchanger` | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m09_coordination_utilities.ExchangerDemo` |
| `PhaserDemo` | Multi-phase coordination with a party dynamically registering between phases | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m09_coordination_utilities.PhaserDemo` |

## ▶️ How to Run
```bash
mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m09_coordination_utilities.<ClassName>
```
Or run the tests for this module only:
```bash
mvn -pl concurrency-lab test -Dtest="com.concurrency.lab.m09_coordination_utilities.*"
```

## 📊 Sample Output
```
== Phase 0: 3 workers register ==
[worker-0] working in phase 0
[worker-1] working in phase 0
[worker-2] working in phase 0
[worker-0] arriving and deregistering after phase 0
[worker-1] arriving and deregistering after phase 0
[worker-2] arriving and deregistering after phase 0
[main] phase 0 complete, all 3 workers arrived
== Phase 1: a 4th worker dynamically registers ==
[worker-3] working in phase 1
[worker-3] arriving and deregistering after phase 1
[main] phase 1 complete, all 4 workers arrived
[main] deregistering owner party, allowing phaser to terminate
Phaser terminated: true
```

## 🔗 Further Reading
- [`CountDownLatch` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [`CyclicBarrier` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CyclicBarrier.html)
- [`Phaser` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Phaser.html)
- Brian Goetz, *Java Concurrency in Practice*, Chapter 5 — Building Blocks
