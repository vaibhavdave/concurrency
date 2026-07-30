# M02 — Race Conditions and `synchronized`

## 🎯 Learning Objectives
- Reproduce a classic lost-update race condition and explain why `count++` is not atomic.
- Fix it with `synchronized` and understand what "mutual exclusion" actually buys you.
- Distinguish coarse-grained locking (`synchronized` method / `synchronized(this)`) from fine-grained locking (a private final lock object per independently-guarded field).
- Recognize why exposing a lock object publicly is dangerous.
- Implement the classic bounded-buffer producer/consumer pattern with intrinsic locks and `wait()`/`notifyAll()`, guarding against spurious wakeup.

## 📖 Concept
`count++` is really three separate steps: read `count`, add 1, write `count` back. If two threads interleave those steps, one thread's update can be silently overwritten:

```
Thread A: read count (0)
Thread B: read count (0)
Thread A: write count = 1
Thread B: write count = 1   <-- Thread A's increment is lost
```

`synchronized` fixes this by giving each thread mutual-exclusive access to a *monitor* (every Java object has one): only one thread can hold a given object's monitor at a time, and acquiring/releasing it also establishes a happens-before edge, so the change is visible to the next thread that acquires the same monitor.

Locking granularity matters: `synchronized` on `this` (or a whole method) serializes ALL callers, even ones touching unrelated state. A private `final Object lock = new Object()` per logically-independent piece of state lets unrelated operations run in parallel while still protecting each piece of state correctly. Never synchronize on a lock object other code outside your class can also reach (like `this` on a class with public methods, or a public field) — arbitrary external code can acquire that same monitor and block/starve your own threads.

The classic bounded buffer uses intrinsic wait/notify:

```mermaid
sequenceDiagram
    participant P as Producer
    participant B as Buffer (monitor)
    participant C as Consumer
    P->>B: synchronized put()
    Note over B: while (full) wait()
    P->>B: addLast(item); notifyAll()
    C->>B: synchronized take()
    Note over B: while (empty) wait()
    C->>B: removeFirst(); notifyAll()
```

The `while` (not `if`) around the wait condition is mandatory: `wait()` can return due to a *spurious wakeup* even without a matching `notify()`, so the condition must always be re-checked after waking up.

## ⚠️ Common Pitfalls
- Treating `i++`/`count++` as atomic — it is read-modify-write and requires synchronization or an atomic type under concurrent access.
- Using `if (condition) wait();` instead of `while (condition) wait();` — vulnerable to spurious wakeups and to another thread stealing the resource between notify and re-acquiring the lock.
- Synchronizing the whole object/method when only a small piece of state actually needs protecting, needlessly serializing unrelated work.
- Synchronizing on a publicly reachable object (`this` on a class with public API, a public field, or an interned `String`/boxed `Integer`) — any other code can acquire the same lock and cause unexpected blocking or deadlock.
- Calling `notify()` instead of `notifyAll()` when multiple different conditions/threads might be waiting on the same monitor — you can wake the wrong waiter and lose a signal.

## 🧪 Demos in This Module
| Class | What it demonstrates | Run command |
|---|---|---|
| `RaceConditionDemo` | `UnsafeCounter` loses updates under concurrent increments; `SafeSynchronizedCounter` never does | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m02_race_conditions_and_synchronized.RaceConditionDemo` (or: run main() from your IDE) |
| `SynchronizedBlockVsMethodDemo` | Coarse-grained vs fine-grained locking, and the danger of a publicly exposed lock object | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m02_race_conditions_and_synchronized.SynchronizedBlockVsMethodDemo` (or: run main() from your IDE) |
| `WaitNotifyBoundedBufferDemo` | Producer/consumer over a fixed-capacity buffer using intrinsic lock + `wait()`/`notifyAll()` | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m02_race_conditions_and_synchronized.WaitNotifyBoundedBufferDemo` (or: run main() from your IDE) |

## ▶️ How to Run
Each demo class has its own `main()` method and is plain Java (no Spring context needed) — run it directly from your IDE, or via Maven's `exec:java` plugin as shown above.

Run this module's tests with:
```
mvn -pl concurrency-lab test -Dtest=m02_race_conditions_and_synchronized.**
```

## 📊 Sample Output
```
== UnsafeCounter (expected 1000000) ==
UnsafeCounter final value = 941873  <-- lost updates!
== SafeSynchronizedCounter (expected 1000000) ==
SafeSynchronizedCounter final value = 1000000
```

## 🔗 Further Reading
- Java Concurrency in Practice, Chapter 2, 3 & 14 (Brian Goetz et al.)
- [Oracle Tutorial: Guarded Blocks (wait/notify)](https://docs.oracle.com/javase/tutorial/essential/concurrency/guardmeth.html)
- [Oracle Tutorial: Intrinsic Locks and Synchronization](https://docs.oracle.com/javase/tutorial/essential/concurrency/locksync.html)
