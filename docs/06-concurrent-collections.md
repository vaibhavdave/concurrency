← [05. Explicit Locks](05-explicit-locks.md) | **06. Concurrent Collections** | Next → [07. Producer/Consumer Queues](07-producer-consumer-blocking-queues.md)

# 06 — Concurrent Collections

## The failure, first

You use `ConcurrentHashMap` specifically *because* it's thread-safe, and you
write the most natural-looking guard imaginable:

```java
if (!map.containsKey("shared-key")) {
    map.put("shared-key", 1);
}
```

Sixteen threads run this 20,000 times each. You expect exactly one thread
to ever "win" the insert. `CompoundActionPitfallDemo` shows the actual
result: some trials report 2, 3, even more threads all believing they were
first. The map itself never got corrupted — `containsKey` and `put` are
each perfectly atomic individually — but nothing stopped thread B from
running `containsKey` (sees: absent) in the gap between thread A's
`containsKey` (also sees: absent) and thread A's `put`. Two threads, both
correctly observing "not present yet," both proceed to write. **Thread-safe
does not mean composable** — that's the entire lesson of this module,
and it catches people who "did everything right" by using a concurrent
collection in the first place.

## Mental model: a safe object is not the same as a safe sequence of calls on it

Think of `ConcurrentHashMap` as a shared filing cabinet with a very good
clerk: any single instruction you give the clerk ("file this," "check if
this exists," "look this up") is handled correctly and safely, no matter
how many people are shouting instructions at once. But if *you* give the
clerk two separate instructions — "check if X is filed" and then, based on
what you hear back, "file X" — someone else can slip their own two
instructions in between yours. The clerk was never at fault; the race lives
in the gap between your two separate requests, a gap the clerk has no way
to know about unless you ask for a single combined instruction instead:
"file X only if it isn't already there" (`putIfAbsent`) — one request, so
there's no gap for anyone to slip into.

## Concept, from first principles

### The fix is always: collapse "read, decide, write" into one call

```java
// racy: two separate requests, a gap in between
if (!map.containsKey(k)) {
    map.put(k, v);
}

// correct: one atomic compound operation, no gap
map.putIfAbsent(k, v);
```

`CompoundActionPitfallDemo` runs both versions side by side under identical
16-thread contention: the racy version reports more than one "winner" in
several trials; the `putIfAbsent` version reports **exactly** one winner,
every single trial, with zero variance. `computeIfAbsent` extends the same
guarantee to a value that must be *computed*, not just supplied:
`map.computeIfAbsent(key, k -> expensiveInit())` runs the mapping function
at most once per key even under contention — the map's own internal
locking (not yours) makes the read-decide-write-a-computed-value sequence
atomic. `compute` and `merge` generalize this further to "atomically
transform whatever is currently there, present or absent":

```java
wordCounts.compute("apple", (k, v) -> v == null ? 1 : v + 1);
wordCounts.merge("cherry", 1, Integer::sum); // 1 if absent, else current + 1
```

Every one of these methods exists for exactly one reason: to give you a
single call that replaces a "read, decide, write" sequence you would
otherwise have to guard with your own external lock.

### Why `ConcurrentHashMap` outperforms `Collections.synchronizedMap`

Both are "thread-safe." They are not equally *scalable*. A
`synchronizedMap`-wrapped `HashMap` funnels every single call — reads and
writes, on any key — through one shared monitor; from the map's
perspective, two threads writing to *completely unrelated* keys still
serialize behind each other. `ConcurrentHashMap` uses lock striping / CAS
per bin internally, so writes to different keys (which usually land in
different bins) rarely contend with each other at all.
`ConcurrentHashMapDemo` measures this directly: 8 threads, each owning its
own distinct key, hammering `merge(key, 1, Integer::sum)` — the
`ConcurrentHashMap` run finishes measurably faster than the identical
workload against a `synchronizedMap`, purely because the map internally
lets non-overlapping writes proceed in parallel instead of forcing them
through one global lock.

### `CopyOnWriteArrayList`: an iterator that can never see the future, and never throws for trying

A plain `ArrayList` mutated by one thread while another iterates it throws
`ConcurrentModificationException` — the iterator detects the structural
change and fails fast rather than silently misbehaving.
`CopyOnWriteArrayList` sidesteps the problem entirely by copying the
**entire backing array** on every `add`/`remove`/`set`. An iterator holds a
reference to one specific array snapshot, taken at iterator-creation time,
forever — it is *structurally incapable* of seeing a later write, because
that write created an entirely new array the iterator never has a
reference to:

```java
List<Integer> list = new CopyOnWriteArrayList<>(/* 0..4 */);
// another thread concurrently adds 5..9
for (Integer value : list) {  // iterates the snapshot taken at loop start
    // never throws CME, and never sees the concurrent additions either
}
```

`CopyOnWriteArrayListDemo` runs exactly this and reports the iteration
completing cleanly over the original 5 elements while a second thread adds
five more concurrently — no exception, no crash, but also no visibility
into the concurrent adds. That's the entire trade-off in one sentence:
**cheap, lock-free, snapshot-consistent reads, in exchange for an O(n)
array copy on every single write.** It's an excellent fit for something
like a listener list (added to rarely, iterated constantly) and a poor fit
for anything with a hot write path.

### `ConcurrentSkipListMap`: `TreeMap`'s ordering, safe for concurrent readers and writers

`TreeMap` is not thread-safe at all under concurrent mutation.
`ConcurrentSkipListMap` gives you the same sorted-map API — `firstKey`,
`ceilingKey`, `headMap`/`tailMap`/`subMap` — backed by a skip list (a
layered linked structure) that supports lock-free CAS-based concurrent
inserts, so multiple threads can insert simultaneously and the map stays
correctly sorted throughout:

```java
Map<Integer, String> head = map.headMap(50);     // keys strictly < 50
Map<Integer, String> tail = map.tailMap(1550);    // keys >= 1550
Map<Integer, String> sub  = map.subMap(100, 110); // 100 inclusive .. 110 exclusive
```

`ConcurrentSkipListMapDemo` has 8 threads insert 1,600 keys concurrently and
confirms both that every key lands (no lost inserts) and that the range
views are correct afterward. The one detail worth internalizing: these
range views are **live windows** backed by the same underlying map, not
copies — a later mutation to the map is reflected through an
already-obtained `headMap`/`tailMap`/`subMap` view, exactly like `TreeMap`.

## Misconceptions worth naming directly

- **Belief: "The map is thread-safe, so any sequence of calls I make on it
  is safe too."**
  Wrong — thread-safety is a per-method guarantee, not a per-sequence one.
  Proof: `CompoundActionPitfallDemo`'s racy `containsKey`-then-`put` reports
  multiple "winners" across trials against a genuinely thread-safe
  `ConcurrentHashMap`.

- **Belief: "Since `Collections.synchronizedMap` makes every call
  thread-safe, it should scale about the same as `ConcurrentHashMap`."**
  Wrong — `synchronizedMap` serializes *all* access behind one lock
  regardless of which keys are touched, while `ConcurrentHashMap` lets
  writes to different bins proceed independently; the difference shows up
  directly as a throughput gap under concurrent access to distinct keys.

- **Belief: "`CopyOnWriteArrayList` never throwing `ConcurrentModification-
  Exception` means it's just a strictly better `ArrayList`."**
  Wrong — the reason it never throws is that its iterators are permanently
  blind to any write that happens after they were created, and every write
  costs a full array copy; using it for a write-heavy list trades away
  performance for a guarantee (stable iteration) many write-heavy use cases
  don't even need.

- **Belief: "`map.size()` on a concurrent collection gives me the exact
  count at the instant I call it, even with writers active."**
  Wrong — like most concurrent-collection sizes under active concurrent
  modification, it's a best-effort estimate, not a value frozen at a single
  instant, unless writers have genuinely stopped.

## Where this shows up for real

The `containsKey`-then-`put` race is one of the most common real-world bugs
in caches, deduplication logic, and idempotency checks — "make sure this
only happens once" almost always wants `putIfAbsent`/`computeIfAbsent`, not
a manual check. `CopyOnWriteArrayList` is the standard choice for
listener/observer lists in frameworks (added to rarely at startup, iterated
on every event) for exactly the reason this module explains. This module's
lock-striping intuition for `ConcurrentHashMap` is also the conceptual
bridge to module 12's lock-free structures — "different keys/bins rarely
contend" is the same idea as "different memory locations rarely contend,"
just with more infrastructure around it.

## Check yourself

1. Why can `if (!map.containsKey(k)) map.put(k, v)` produce more than one
   "winner" even when `map` is a genuinely thread-safe `ConcurrentHashMap`?
2. Name the single-call replacement for that racy pattern, and explain in
   one sentence *why* it closes the race that the two-call version leaves
   open.
3. Why does `ConcurrentHashMap` typically outperform
   `Collections.synchronizedMap` under concurrent writes to different
   keys, when both are "thread-safe"?
4. Why does iterating a `CopyOnWriteArrayList` never throw
   `ConcurrentModificationException`, and what's the real cost you pay in
   exchange?

---

<details>
<summary>Answers</summary>

1. Because `containsKey` and `put` are each atomic on their own, but the
   *sequence* of the two is not — another thread can execute its own
   `containsKey` in the gap between this thread's `containsKey` and its
   `put`, so both threads can observe "absent" and both proceed to write.
2. `map.putIfAbsent(k, v)` — it performs the check and the write as a
   single atomic operation inside the map's own internals, leaving no gap
   between "read" and "write" for another thread to interleave into.
3. `ConcurrentHashMap` uses lock striping/CAS per bin, so writes to
   different keys usually touch different bins and rarely contend with
   each other; `synchronizedMap` funnels every call, regardless of key,
   through one shared monitor, serializing unrelated writes unnecessarily.
4. Because each iterator holds a reference to one fixed array snapshot
   taken at creation time, and mutation always creates an entirely new
   array rather than modifying the one the iterator holds — the iterator
   is structurally unable to observe (or be invalidated by) a later write.
   The cost is an O(n) full-array copy on every single add/remove/set,
   making it a poor fit for write-heavy usage.

</details>

---

← [05. Explicit Locks](05-explicit-locks.md) | Next → [07. Producer/Consumer Queues](07-producer-consumer-blocking-queues.md)
