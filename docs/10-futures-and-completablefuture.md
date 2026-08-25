← [09. Coordination Utilities](09-coordination-utilities.md) | **10. Futures & CompletableFuture** | Next → [11. Fork/Join & Parallel Streams](11-fork-join-and-parallel-streams.md)

# 10 — Futures and CompletableFuture

## The failure, first

You submit three independent async calls to three services, and you want
to do something once all three are done. With a plain `Future`, the only
tool you have is `get()` — which **blocks the calling thread**. So you call
`get()` on the first, block until it's done, then `get()` on the second,
block again, then the third — even though all three might have finished
concurrently ages ago. There is no way to say "call me back when this is
ready," no way to combine two `Future`s into one, no way to react to a
failure without first blocking to discover it. A `Future` is a receipt for
a result, but the only thing you can do with the receipt is stand in line
and wait.

`FutureLimitationsDemo` walks through this directly: `get()` blocks for the
full duration of a 200ms task; `get(timeout)` at least lets you give up and
throw `TimeoutException` instead of blocking forever; but even
`cancel(true)` is weaker than it looks — it only *requests* interruption by
flipping the interrupt flag, and a task that never checks
`Thread.interrupted()` (or never calls an interruptible blocking method)
simply ignores the request and keeps running to completion regardless.
`CompletableFuture` exists because none of this is composable, and modern
concurrent code is almost always about composing several async operations,
not running exactly one and waiting for it.

## Mental model: a pipeline of stages, not a single receipt

A plain `Future` is a receipt you can only redeem by standing in line.
`CompletableFuture` is a **pipeline**: each stage is triggered automatically
by the completion of the stage(s) before it, and you never have to
personally stand and wait for anything unless you choose to.

```
supplyAsync(task)
      |
      v
  thenApply(fn) ---------------------\
      |                               v
  thenCompose(fn2)              exceptionally(recover)
      |                               |
      v                               v
  thenCombine(other, fn3) ----> handle(result, ex) ----> whenComplete(observe) ----> get()/join()
```

Every method in this diagram attaches a *callback* to a future stage — none
of them block the calling thread to install the callback; only `get()`/
`join()` at the very end (if you even call them) actually wait.

## Concept, from first principles

### Transform, chain, and merge — `thenApply`, `thenCompose`, `thenCombine`

`CompletableFutureChainingDemo` distinguishes three shapes of "do something
next" that are easy to conflate:

```java
// thenApply: transform a value in place — like Stream.map
CompletableFuture.supplyAsync(() -> 10, executor).thenApply(v -> v * 2);

// thenCompose: chain to ANOTHER CompletableFuture-returning step — like flatMap
CompletableFuture.supplyAsync(() -> 5, executor)
    .thenCompose(v -> CompletableFuture.supplyAsync(() -> "computed:" + (v * v), executor));

// thenCombine: merge two INDEPENDENT futures once both finish
CompletableFuture<Integer> left = CompletableFuture.supplyAsync(() -> 3, executor);
CompletableFuture<Integer> right = CompletableFuture.supplyAsync(() -> 4, executor);
left.thenCombine(right, Integer::sum);
```

The `thenApply` vs. `thenCompose` distinction matters for exactly the same
reason `map` vs. `flatMap` matters on a `Stream`: if the function you're
chaining itself returns a `CompletableFuture`, using `thenApply` would give
you a `CompletableFuture<CompletableFuture<T>>` — a future wrapping a
future, useless without unwrapping it yourself. `thenCompose` flattens that
automatically, because the whole point of chaining async steps is to avoid
ever nesting them.

### Every callback runs *somewhere* — and `*Async` is how you choose where

This is the detail that catches people who've used `thenApply` correctly a
hundred times and never noticed: a plain `thenApply`/`thenAccept`/`thenRun`
callback may run on **whichever thread happened to complete the previous
stage** — which could be the thread that called `supplyAsync`, or a pool
thread, depending purely on timing (was the previous stage already done
when this callback was attached, or not yet?). The `*Async` variants remove
that ambiguity entirely:

```java
CompletableFuture.supplyAsync(() -> { /* runs on `executor` */ return 7; }, executor)
    .thenApplyAsync(v -> { /* GUARANTEED to run on `executor`, not the completing thread */ return v + 1; }, executor)
    .thenAcceptAsync(v -> { /* also guaranteed on `executor` */ }, executor);
```

This matters whenever you need to keep certain work *off* a particular
thread — for instance, off an event-loop thread that must stay responsive,
or off whatever pool happened to run the upstream task — by explicitly
routing the continuation to a pool you control. Leave off the `Executor`
argument entirely and `*Async` methods default to the common
`ForkJoinPool` (module 11) — fine for quick callbacks, a real risk for
anything CPU-heavy that would then compete with unrelated parallel streams
elsewhere in the same JVM.

### Three ways to react to failure, and they are not interchangeable

`CompletableFutureExceptionHandlingDemo` runs all three side by side, which
is the clearest way to see they answer different questions:

```java
// exceptionally: ONLY runs on failure; produces a recovery value
future.exceptionally(ex -> -1);

// handle: ALWAYS runs, success or failure; sees BOTH (result, exception) — exactly one is non-null
future.handle((value, ex) -> ex != null ? "error:" + ex : "value:" + value);

// whenComplete: ALWAYS runs, sees BOTH — but its return value is IGNORED; it cannot recover
future.whenComplete((value, ex) -> {
    if (ex != null) { log(ex); }   // observing only — the original failure still propagates
});
```

The demo makes the `whenComplete` distinction concrete: even after
`whenComplete` logs the failure, `get()` on that future still throws —
`whenComplete` is purely an observer (think "log this, either way"), never
a recovery mechanism. Only `exceptionally` and `handle` can actually change
what the future ultimately completes with. And a failure inside any stage
of a chain **short-circuits every subsequent `thenApply`/`thenCompose`**
until it reaches a stage that knows how to handle it (`exceptionally` or
`handle`) — the demo's `chainWithFailure` example shows a `thenApply` that
throws, followed by a second `thenApply` that simply never runs at all,
skipped entirely on the way to the `exceptionally` that finally catches it.

### Timeouts and fan-out/fan-in

```java
future.orTimeout(100, TimeUnit.MILLISECONDS);              // fails with TimeoutException if not done in time
future.completeOnTimeout("fallback-value", 100, TimeUnit.MILLISECONDS); // succeeds with a fallback instead

CompletableFuture.allOf(a, b, c);   // completes once ALL finish; any one failing fails the aggregate
CompletableFuture.anyOf(a, b, c);   // completes as soon as the FIRST one finishes
```

`CompletableFutureTimeoutAndCombiningDemo` shows the sharp edge in
`allOf`: it returns `CompletableFuture<Void>`, not a list of results — you
must `.join()` each individual future afterward to actually retrieve its
value, since `allOf` itself only tells you "everyone is done," not "here's
what everyone produced." And when one of the futures passed to `allOf`
fails, the aggregate future fails too (fast), but the *other*, still-
succeeding futures remain independently retrievable via their own
`.join()` — one failure doesn't erase the results that did succeed.

## Misconceptions worth naming directly

- **Belief: "`future.cancel(true)` stops the task from running."**
  Wrong — it only sets the target thread's interrupt flag; a task that
  never checks `Thread.interrupted()` or blocks in an interruptible call
  simply never notices and runs to completion regardless. Proof:
  `FutureLimitationsDemo`'s cancelled future still transitions through
  `isCancelled()`, but only because the specific task happened to be
  sleeping (an interruptible call) — a CPU-bound loop with no interrupt
  check would ignore the cancellation entirely.

- **Belief: "`thenApply` and `thenCompose` are interchangeable; just pick
  whichever reads better."**
  Wrong when the function passed to it returns a `CompletableFuture` —
  `thenApply` there produces a nested
  `CompletableFuture<CompletableFuture<T>>`, which is almost never what you
  want; `thenCompose` is required to flatten it.

- **Belief: "A `thenApply` callback runs on the same thread that ran
  `supplyAsync`'s task, so I know exactly which thread it's on."**
  Wrong — it runs on whichever thread completes the *previous* stage,
  which can be the original submitter's thread or a pool thread depending
  on timing; only the `*Async` variants with an explicit `Executor`
  guarantee where the callback runs.

- **Belief: "`whenComplete` can recover a failure, since it sees the
  exception, just like `handle` does."**
  Wrong — its return value is discarded entirely; it can observe a failure
  but never change the outcome, so the original exception still propagates
  to `get()`/`join()` regardless of what `whenComplete`'s body does.

- **Belief: "`allOf(a, b, c).get()` gives me the combined results."**
  Wrong — `allOf` returns `CompletableFuture<Void>`; it only signals
  completion (or the first failure). Retrieving actual values requires
  calling `.join()` on each original future afterward.

## Where this shows up for real

This is the standard shape of any service that fans out to multiple
downstream calls (a search page hitting three backend services in
parallel, then combining results) — `allOf`/`thenCombine` is exactly that
pattern. The `*Async`-with-explicit-`Executor` discipline is how real
systems keep blocking or CPU-heavy work off latency-sensitive threads
(request-handling threads, event loops). `orTimeout`/`completeOnTimeout`
are the direct building blocks for implementing per-call timeouts and
graceful degradation (serve a cached/fallback value when a downstream call
is too slow) without writing your own timer/cancellation plumbing.

## Check yourself

1. What specifically can a plain `Future` not do, that a
   `CompletableFuture` chain can — name the concrete capability, not just
   "it's better"?
2. Why does chaining a function that itself returns a `CompletableFuture`
   require `thenCompose` instead of `thenApply`?
3. Without using an `*Async` variant with an explicit `Executor`, which
   thread runs a `thenApply` callback, and why is that not fully
   predictable?
4. Contrast what `exceptionally`, `handle`, and `whenComplete` each do when
   the upstream future fails — specifically, which ones can change what
   the future ultimately completes with?
5. Why does `CompletableFuture.allOf(a, b, c).get()` not give you `a`,
   `b`, and `c`'s actual results directly?

---

<details>
<summary>Answers</summary>

1. A plain `Future` can only be waited on via a blocking `get()` — it has
   no way to register a callback to run on completion, no way to combine
   it with another `Future`, and no way to react to failure without first
   blocking. `CompletableFuture` adds exactly this: composable stages
   (`thenApply`/`thenCompose`/`thenCombine`) and failure handling
   (`exceptionally`/`handle`) that don't require blocking to attach.
2. Because `thenApply` would wrap the inner `CompletableFuture` inside an
   outer one, producing a nested `CompletableFuture<CompletableFuture<T>>`
   — `thenCompose` flattens the two into a single `CompletableFuture<T>`,
   the same reason `flatMap` exists alongside `map` for `Stream`.
3. It runs on whichever thread happens to complete the *previous* stage —
   which could be the original thread that called `supplyAsync` (if the
   callback is attached before that stage finishes) or a pool thread,
   depending purely on the timing of when the callback gets attached
   relative to when the prior stage completes.
4. `exceptionally` only runs on failure and supplies a recovery value —
   it can change the outcome. `handle` always runs (success or failure)
   and can also change the outcome by returning a new value regardless of
   which case it's in. `whenComplete` always runs too, but its return
   value is discarded — it can only observe, never recover; the original
   exception (if any) still propagates afterward.
5. Because `allOf` returns `CompletableFuture<Void>` — it only signals that
   every future has completed (or that one has failed), not what any of
   them produced. Retrieving actual values requires calling `.join()` on
   each individual future afterward.

</details>

---

← [09. Coordination Utilities](09-coordination-utilities.md) | Next → [11. Fork/Join & Parallel Streams](11-fork-join-and-parallel-streams.md)
