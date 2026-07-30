# M06 — Concurrent Collections

## 🎯 Learning Objectives
- Use `ConcurrentHashMap`'s atomic compound operations (`putIfAbsent`, `compute`, `computeIfAbsent`, `merge`) instead of hand-rolled locking.
- Understand why `ConcurrentHashMap` scales better than `Collections.synchronizedMap(...)` under concurrent access.
- Know when `CopyOnWriteArrayList` is the right (and the wrong) tool, and why it never throws `ConcurrentModificationException`.
- Use `ConcurrentSkipListMap` for a sorted, concurrent map with live range views.
- Recognize the classic "check-then-act" race on a thread-safe collection and fix it with a single atomic call.

## 📖 Concept
Thread-safe does **not** mean "every operation on this object is safe to combine with other operations." Each individual method on `ConcurrentHashMap`, `CopyOnWriteArrayList`, etc. is atomic and safe to call from multiple threads — but *sequences* of calls (a "compound action") are not automatically atomic just because the collection is thread-safe.

```
Thread A                          Thread B
--------                          --------
if (!map.containsKey(k))
                                   if (!map.containsKey(k))
                                   map.put(k, v)   // B "wins"
map.put(k, v)                     // A overwrites B -- both thought they were first
```

The fix is always the same shape: replace "read, decide, write" with a single method that does all three atomically inside the collection's own internals — `putIfAbsent`, `computeIfAbsent`, `compute`, `merge`.

Mental model for each collection in this module:
- **`ConcurrentHashMap`** — lock striping / CAS on individual bins. Different keys rarely contend with each other; only concurrent writers to the *same* key or bin serialize.
- **`CopyOnWriteArrayList`** — every mutation (`add`/`remove`/`set`) copies the entire backing array. Iterators hold a reference to one array snapshot forever, so they can't see later writes and can't throw `ConcurrentModificationException`. Cheap, lock-free reads; expensive writes.
- **`ConcurrentSkipListMap`** — a concurrent, lock-free sorted map (skip list instead of a red-black tree), giving `ceilingKey`, `headMap`, `tailMap`, `subMap` with the same live-view semantics as `TreeMap`, but safe for concurrent readers and writers.

## ⚠️ Common Pitfalls
- `if (!map.containsKey(k)) map.put(k, v)` on a `ConcurrentHashMap` — looks safe because the map is thread-safe, but the two calls are not atomic together. Use `putIfAbsent` or `computeIfAbsent`.
- Iterating a plain `HashMap`/`ArrayList` while another thread mutates it — throws (or silently corrupts state via) `ConcurrentModificationException`. Wrapping with `Collections.synchronizedMap`/`synchronizedList` only makes *individual* calls thread-safe; iteration still needs external `synchronized` around the whole loop, or a genuinely concurrent collection.
- Treating `CopyOnWriteArrayList` as a general-purpose list — a hot write path (e.g. a per-request mutated list) will pay for an O(n) array copy on every single write.
- Forgetting that `CopyOnWriteArrayList` iterators are snapshots: code that expects `for (x : list)` to observe concurrent `add`s from another thread will be surprised when it doesn't.
- Assuming `ConcurrentHashMap.size()` is a precise, momentarily-frozen count under concurrent modification — like most concurrent collection sizes, it's a best-effort estimate unless you've otherwise quiesced writers.

## 🧪 Demos in This Module
| Class | What it demonstrates | Run command |
|---|---|---|
| `ConcurrentHashMapDemo` | `putIfAbsent`/`compute`/`computeIfAbsent`/`merge` as atomic ops; throughput vs `synchronizedMap` | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m06_concurrent_collections.ConcurrentHashMapDemo` |
| `CopyOnWriteArrayListDemo` | Safe snapshot iteration vs plain `ArrayList`'s `ConcurrentModificationException`; cost tradeoff | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m06_concurrent_collections.CopyOnWriteArrayListDemo` |
| `ConcurrentSkipListMapDemo` | Concurrent sorted inserts; `headMap`/`tailMap`/`subMap` range views | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m06_concurrent_collections.ConcurrentSkipListMapDemo` |
| `CompoundActionPitfallDemo` | The `containsKey`-then-`put` race, and the `putIfAbsent` fix | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m06_concurrent_collections.CompoundActionPitfallDemo` |

## ▶️ How to Run
Run any demo's `main()` directly via `exec:java` (see the table) or from your IDE.

Run this module's tests only:
```
mvn -pl concurrency-lab test -Dtest=m06_concurrent_collections.**
```
or a single class:
```
mvn -pl concurrency-lab test -Dtest=ConcurrentHashMapTest
```

## 📊 Sample Output
```
== 1. Racy 'if (!map.containsKey(k)) map.put(k, v)' ==
Trial 0: 3 thread(s) believed they were first to insert  <-- RACE DETECTED
Trial 1: 1 thread(s) believed they were first to insert
...
== 2. Fixed with putIfAbsent() (single atomic compound operation) ==
Trial 0: 1 thread(s) won the insert (always exactly 1)
Trial 1: 1 thread(s) won the insert (always exactly 1)
```

## 🔗 Further Reading
- [`ConcurrentHashMap` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [`CopyOnWriteArrayList` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)
- [`ConcurrentSkipListMap` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html)
- Brian Goetz, *Java Concurrency in Practice*, Chapter 5 (Building Blocks) — client-side locking and compound actions on concurrent collections
