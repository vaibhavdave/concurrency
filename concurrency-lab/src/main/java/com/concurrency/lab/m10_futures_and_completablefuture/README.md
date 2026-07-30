# M10 — Futures and CompletableFuture

## 🎯 Learning Objectives
- Recognize the limitations of the plain `Future` API: blocking `get()`, coarse `cancel()` semantics, no composition
- Build async pipelines with `thenApply`/`thenCompose`/`thenCombine`/`thenAccept`/`thenRun`
- Understand what the `*Async` variants change: which `Executor` runs the continuation
- Handle failures with `exceptionally`, `handle`, and `whenComplete`, and know how each differs
- Apply timeouts with `orTimeout`/`completeOnTimeout`, and fan out/in with `allOf`/`anyOf`

## 📖 Concept
A plain `Future<T>` is a handle to a result that isn't ready yet — but the only way to get the result is to
block on `get()`. There's no way to say "run this callback when it's done" or "combine two Futures." `CompletableFuture<T>`
adds exactly that: a pipeline of stages, each triggered by the completion of the previous one(s).

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

- `thenApply` transforms a value (like `map`); `thenCompose` chains to another `CompletableFuture`-returning
  function (like `flatMap`), avoiding a nested `CompletableFuture<CompletableFuture<T>>`.
- `thenCombine` merges two *independent* futures once both finish; `allOf`/`anyOf` fan in over N futures — `allOf`
  waits for every one (any failure fails the aggregate), `anyOf` completes as soon as the first one does.
- Every non-`Async` callback (`thenApply`, `thenAccept`, ...) may run on the thread that completed the *previous*
  stage — which could be the original submitting thread, or a pool thread, depending on timing. The `*Async`
  variants (`thenApplyAsync`, ...) guarantee the callback runs on the supplied `Executor` (or the common
  `ForkJoinPool` if none is given), which matters when you need to keep work off pool threads that must stay
  responsive (e.g., an event loop) or explicitly control which pool absorbs the load.
- `exceptionally` only runs on failure and produces a recovery value. `handle` always runs, on success *or*
  failure, and receives both `(result, exception)` — exactly one is non-null. `whenComplete` also always runs
  and receives both, but its return value is ignored: it observes/logs but never changes the outcome, so a
  failure it sees still propagates to `get()`.

## ⚠️ Common Pitfalls
- Calling `Future.get()` with no timeout in a request-handling thread — an unresponsive task blocks it forever
- Assuming `cancel(true)` always stops a running task — it only sets the interrupt flag; the task must check
  `Thread.interrupted()` or respond to `InterruptedException` to actually stop
- Using `whenComplete` expecting it to suppress/recover a failure the way `exceptionally`/`handle` do — it does not
- Forgetting that `get()` wraps the underlying exception in `ExecutionException`/`CompletionException` — you must
  unwrap via `getCause()` to see the real failure; `join()` throws unchecked `CompletionException` directly
- Mixing `orTimeout`'s `TimeoutException` handling: it surfaces as `ExecutionException` from `get()` but as
  `CompletionException` from `join()` or a downstream `exceptionally` — handle both shapes
- Using `allOf(...).get()` directly to retrieve results — `allOf` returns `CompletableFuture<Void>`; you must
  `.join()` each individual future afterward to get its value
- Leaking the default `ForkJoinPool.commonPool()` for CPU-bound `*Async` work that competes with unrelated
  parallel streams elsewhere in the JVM — pass an explicit `Executor` for anything non-trivial

## 🧪 Demos in This Module
| Class | What it demonstrates | Run command |
|---|---|---|
| `FutureLimitationsDemo` | `Future.get()` blocking, `cancel()` semantics, lack of composition | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m10_futures_and_completablefuture.FutureLimitationsDemo` |
| `CompletableFutureChainingDemo` | `thenApply`/`thenCompose`/`thenCombine`/`thenAccept`/`thenRun` and the `*Async` variants | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m10_futures_and_completablefuture.CompletableFutureChainingDemo` |
| `CompletableFutureExceptionHandlingDemo` | `exceptionally`, `handle`, `whenComplete`, and exception propagation through a chain | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m10_futures_and_completablefuture.CompletableFutureExceptionHandlingDemo` |
| `CompletableFutureTimeoutAndCombiningDemo` | `orTimeout`/`completeOnTimeout`, and fan-out/fan-in with `allOf`/`anyOf` | `mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m10_futures_and_completablefuture.CompletableFutureTimeoutAndCombiningDemo` |

## ▶️ How to Run
```bash
mvn -pl concurrency-lab exec:java -Dexec.mainClass=com.concurrency.lab.m10_futures_and_completablefuture.<ClassName>
```
Or run the tests for this module only:
```bash
mvn -pl concurrency-lab test -Dtest="com.concurrency.lab.m10_futures_and_completablefuture.*"
```

## 📊 Sample Output
```
== allOf: wait for several independent async calls, then aggregate all results ==
allOf aggregated=[A-result, B-result, C-result]
== anyOf: proceed as soon as the FIRST of several futures completes ==
anyOf first result=fast-service
== allOf with one failing future: the combined future fails, but individual results are still retrievable ==
allOf failed fast because failingService failed: downstream-unavailable
healthyService still completed normally: healthy
```

## 🔗 Further Reading
- [`CompletableFuture` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [`Future` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html)
- Brian Goetz, *Java Concurrency in Practice*, Chapter 6 — Task Execution
