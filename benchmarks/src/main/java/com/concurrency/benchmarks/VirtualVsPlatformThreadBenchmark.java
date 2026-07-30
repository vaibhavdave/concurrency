package com.concurrency.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Companion benchmark for m14 (virtual threads): total wall-clock time to
 * run a batch of short blocking tasks on a bounded platform-thread pool vs.
 * one virtual thread per task. SingleShotTime measures whole-batch latency
 * rather than steady-state throughput, which is the right lens for "how long
 * until these 5,000 blocking calls all finish".
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
public class VirtualVsPlatformThreadBenchmark {

    private static final int TASK_COUNT = 5_000;
    private static final int SIMULATED_IO_MILLIS = 5;

    @Benchmark
    public void platformThreadsBlockingTasks() throws InterruptedException {
        runBlockingTasks(Executors.newFixedThreadPool(200));
    }

    @Benchmark
    public void virtualThreadsBlockingTasks() throws InterruptedException {
        runBlockingTasks(Executors.newVirtualThreadPerTaskExecutor());
    }

    private void runBlockingTasks(ExecutorService executor) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(TASK_COUNT);
        try (executor) {
            for (int i = 0; i < TASK_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(SIMULATED_IO_MILLIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }
    }
}
