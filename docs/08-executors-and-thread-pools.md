← [07. Producer/Consumer Queues](07-producer-consumer-blocking-queues.md) | **08. Executors & Thread Pools** | Next → [09. Coordination Utilities](09-coordination-utilities.md)

# 08 — Executors and Thread Pools

## The failure, first

A service under a sudden traffic spike submits 500 tasks to a pool built
with `Executors.newFixedThreadPool(4)`. Nothing crashes. Nothing gets
rejected. Memory usage quietly climbs — because `newFixedThreadPool`'s
internal queue is an **unbounded** `LinkedBlockingQueue`, so every excess
task just queues up forever, waiting its turn, no matter how far behind the
4 worker threads fall. There's no backpressure signal at all — the caller
that submitted task 500 gets the exact same "success" as the caller that
submitted task 1, even though task 500 might not run for an hour. This is
module 07's unbounded-queue lesson wearing a thread-pool costume, and it's
one of the most common ways a "the pool will just handle it" assumption
turns into a production incident days later, once the queue has grown
large enough to matter.

## Mental model: a fixed crew, plus a waiting line, plus a hard rule for what happens when the line is also full

A thread pool is a small, reused crew of workers pulling jobs from a
queue, instead of a `new Thread` per task (module 01's lesson that threads
are not free). The mental model that resolves nearly every "why did my pool
do that" question is a strict, ordered admission policy — not "spread work
evenly across idle threads," but a fixed sequence of fallbacks:

```
submit(task) -> [ BlockingQueue ] -> [ worker-1 ][ worker-2 ]...[ worker-N ]
                      ^                                              |
                      |-- if pool at max size AND queue full --------|
                      v
              RejectedExecutionHandler (Abort / CallerRuns / Discard / DiscardOldest)
```

1. Fewer than `corePoolSize` threads exist right now? **Start a new
   thread** for this task, even if other threads are currently idle.
2. Otherwise, try to **put the task in the queue**.
3. Queue full, and fewer than `maximumPoolSize` threads exist? **Start a
   new thread** anyway (beyond core size).
4. Queue full, and already at `maximumPoolSize`? **Hand the task to the
   `RejectedExecutionHandler`** — there is nowhere left to put it.

The counterintuitive part is step 1 versus step 2: a brand-new task is
handed a brand-new thread *before* the executor ever considers queuing it,
as long as core size hasn't been reached — even if every existing thread is
sitting idle at that exact instant. Queuing is what happens only after core
size is already spent.

## Concept, from first principles

### The four `Executors` factories are four different points on that same admission policy

`ExecutorTypesDemo` runs all four side by side, and each is really just a
different choice of core size, max size, and queue:

- **`newFixedThreadPool(n)`** — core = max = `n`, **unbounded** queue. Tasks
  beyond `n` concurrent ones simply queue forever; steps 3/4 above never
  trigger, because the queue never reports "full."
- **`newCachedThreadPool()`** — core = 0, max = `Integer.MAX_VALUE`,
  queue = `SynchronousQueue` (capacity zero, from module 07). Since the
  queue can never actually hold anything, step 2 always "fails" instantly,
  so the pool falls straight to step 3: spin up a new thread for every task
  that doesn't find an idle one waiting. A burst of slow tasks can create
  thousands of threads with no upper bound at all.
- **`newSingleThreadExecutor()`** — core = max = 1, unbounded queue.
  Guarantees strict FIFO, one task at a time, never concurrently — useful
  specifically when tasks must never run concurrently with each other.
- **`newScheduledThreadPool(n)`** — the same core-pool mechanics, but adds
  `schedule`/`scheduleAtFixedRate`/`scheduleWithFixedDelay` for delayed and
  recurring work (see below).

### Building the policy explicitly, and watching each rejection strategy differ

`ThreadPoolExecutorTuningDemo` builds a raw `ThreadPoolExecutor` with core=2,
max=2, and a 2-slot bounded queue — so at most 4 tasks (2 running + 2
queued) can ever be admitted at once — then submits 8 tasks under each of
the four `RejectedExecutionHandler`s:

```java
new ThreadPoolExecutor(2, 2, 5, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), handler);
```

- **`AbortPolicy`** (the default) — throws `RejectedExecutionException` on
  the calling thread for any task beyond the 4 admitted. If nothing catches
  it, the caller crashes.
- **`CallerRunsPolicy`** — the *submitting* thread runs the rejected task
  itself, synchronously, right there. This is the interesting one: it
  provides real backpressure, because the submitter is now busy running
  work instead of submitting more of it, which naturally throttles the
  producer instead of dropping or crashing.
- **`DiscardPolicy`** — silently drops the task. No exception, no
  execution, no trace it ever existed — dangerous unless losing that
  specific unit of work is genuinely acceptable.
- **`DiscardOldestPolicy`** — evicts the oldest *queued* (not yet running)
  task to make room for the new one, favoring newer work over older.

The four policies aren't just different error-handling styles — they
encode fundamentally different product decisions about what should happen
when demand exceeds capacity (reject visibly, self-throttle, drop silently,
or prefer freshness), and picking one is a decision about your system's
actual requirements, not a technical detail.

### Shutdown: two different verbs, and a sequence that uses both

```java
pool.shutdown();     // stop accepting NEW tasks; let queued + running tasks finish
pool.shutdownNow();  // interrupt running tasks NOW; return the never-started ones, still queued
```

`GracefulShutdownDemo` shows the asymmetry directly: `shutdown()` on a pool
with one 100ms task in flight lets it complete normally before terminating.
`shutdownNow()` on a pool running a 5-second task interrupts it immediately
and hands back a `List<Runnable>` of everything that was still queued and
never got to start. Neither call, by itself, tells you when the pool is
actually done — that's what `awaitTermination(timeout, unit)` is for, and
skipping it is how a caller (or the JVM on exit) proceeds as if shutdown
were instant when workers might still be finishing up. The recommended
sequence, straight from the `ExecutorService` javadoc and reproduced in the
demo, escalates deliberately:

```java
pool.shutdown();
if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
    pool.shutdownNow();                          // escalate: stop being polite
    pool.awaitTermination(2, TimeUnit.SECONDS);   // wait once more for the forced interrupt to land
}
```

Try graceful first; only force it if graceful didn't finish in time; then
wait once more, because `shutdownNow()`'s interrupt still takes a moment to
actually land on a running thread.

### `scheduleAtFixedRate` vs. `scheduleWithFixedDelay`: what happens when a run overruns

Both schedule recurring work, but they answer "what if one run takes longer
than the period?" completely differently:

- **`scheduleAtFixedRate(task, initialDelay, period, unit)`** anchors every
  run to `initialDelay + n * period`, measured from the *start*. If one run
  overruns the period, the next run fires **immediately** to catch up
  (never actually overlapping on a single-threaded scheduler, but back to
  back with no gap) — the schedule itself never drifts.
- **`scheduleWithFixedDelay(task, initialDelay, delay, unit)`** waits
  `delay` after each run **finishes**. An overrun here permanently pushes
  every later run later — the schedule drifts forward forever once any run
  overruns.

Which one is correct depends entirely on whether you care about "run
exactly every N seconds by the clock, catching up if needed" (fixed rate)
or "always leave at least N seconds of breathing room between the end of
one run and the start of the next" (fixed delay).

## Misconceptions worth naming directly

- **Belief: "`newFixedThreadPool` will apply backpressure once its threads
  are all busy — that's what 'fixed' means."**
  Wrong — it uses an unbounded queue, so tasks beyond the fixed thread
  count simply queue forever with no rejection and no limit, hiding
  unbounded memory growth behind what looks like a bounded pool.

- **Belief: "A brand-new task always gets queued if any threads are
  already busy, and only creates a new thread once the queue is full."**
  Wrong — a new thread is created immediately whenever fewer than
  `corePoolSize` threads exist, *before* the executor even attempts to
  queue the task, regardless of whether other threads happen to be idle at
  that moment.

- **Belief: "`AbortPolicy`, being the default, must be the safest choice."**
  Wrong to assume without checking — `AbortPolicy` throws on the calling
  thread, and an uncaught `RejectedExecutionException` crashes that caller;
  it's the correct choice only when you actually want visible, immediate
  failure on overload, and you've made sure something catches it.

- **Belief: "`shutdown()` stops all activity right away, so I don't need
  `awaitTermination`."**
  Wrong — `shutdown()` only stops new submissions; queued and running tasks
  keep going. Proceeding without `awaitTermination` means the caller (or
  the JVM) can move on while workers are still mid-task.

- **Belief: "`scheduleAtFixedRate` guarantees no two runs ever execute
  concurrently."**
  True only for a single-threaded scheduler — a `newScheduledThreadPool(n)`
  with `n > 1` can genuinely run two overlapping instances of the same
  scheduled task concurrently if one overruns its period; "fixed rate"
  is about timing, not mutual exclusion between runs.

## Where this shows up for real

Every HTTP server, message consumer, and background-job runner you'll ever
operate is a thread pool with exactly these same four admission-policy
decisions baked into its configuration — a saturated web server returning
503s is `AbortPolicy`'s idea made visible at the API layer; a server that
slows down gracefully under load instead of dropping requests is
`CallerRunsPolicy`'s idea. The `shutdown()`/`shutdownNow()`/
`awaitTermination` sequence is exactly what a graceful pod termination
handler (Kubernetes `preStop` hook, or a JVM shutdown hook) needs to run
before the process actually exits, to avoid dropping in-flight work.

## Check yourself

1. Why can `Executors.newFixedThreadPool(4)`'s task count climb far beyond
   4 with no error or rejection, even though the pool is "fixed" at 4
   threads?
2. Under the four-step admission policy, when exactly does a
   `ThreadPoolExecutor` create a new thread beyond `corePoolSize`, versus
   handing a task to the `RejectedExecutionHandler`?
3. Why does `CallerRunsPolicy` act as a form of backpressure, rather than
   just another way to "handle" a rejected task?
4. You call `pool.shutdown()` and immediately check `pool.isTerminated()`
   without ever calling `awaitTermination`. Why can this report `false`
   even though shutdown "succeeded"?
5. A scheduled task occasionally takes longer than its configured period.
   Contrast what happens to the *next* run's timing under
   `scheduleAtFixedRate` versus `scheduleWithFixedDelay`.

---

<details>
<summary>Answers</summary>

1. Because `newFixedThreadPool` backs its 4 threads with an **unbounded**
   queue — tasks beyond what the 4 threads can immediately run simply
   queue up with no limit and no rejection, since the queue never reports
   itself as full.
2. A new thread beyond `corePoolSize` is created only once the queue is
   already full *and* the pool hasn't yet reached `maximumPoolSize`. Only
   once the queue is full **and** `maximumPoolSize` is already reached does
   a task go to the `RejectedExecutionHandler` — there's genuinely nowhere
   left to put it.
3. Because it makes the *submitting* thread execute the rejected task
   itself, synchronously — that thread is now busy doing work instead of
   submitting more, which naturally slows down the rate of new submissions
   rather than dropping or crashing on overload.
4. Because `shutdown()` only stops accepting new submissions — queued and
   already-running tasks continue executing. The pool isn't actually
   terminated until all of that in-flight work finishes, which is exactly
   what `awaitTermination` waits for; checking immediately after
   `shutdown()` can easily observe the pool still mid-shutdown.
5. Under `scheduleAtFixedRate`, an overrun causes the next run to fire
   immediately afterward to catch up — the absolute schedule
   (`initialDelay + n*period`) never drifts. Under
   `scheduleWithFixedDelay`, the delay is measured from when the previous
   run *finished*, so an overrun permanently pushes every subsequent run
   later — the schedule drifts forward and never catches back up.

</details>

---

← [07. Producer/Consumer Queues](07-producer-consumer-blocking-queues.md) | Next → [09. Coordination Utilities](09-coordination-utilities.md)
