package com.concurrency.lab.m14_virtual_threads_structured_concurrency;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlatformThreadsThroughputDemo {

    private static final int TASK_COUNT = 10_000;
    private static final int POOL_SIZE = 200;
    private static final long SIMULATED_IO_MILLIS = 50;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== Platform-thread pool throughput ==");
        System.out.println("Submitting " + TASK_COUNT + " blocking tasks to a fixed pool of " + POOL_SIZE + " platform threads");

        CountDownLatch allDone = new CountDownLatch(TASK_COUNT);
        Instant start = Instant.now();

        try (ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE)) {
            for (int i = 0; i < TASK_COUNT; i++) {
                pool.submit(() -> {
                    try {
                        Thread.sleep(SIMULATED_IO_MILLIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        allDone.countDown();
                    }
                });
            }
            allDone.await();
        }

        Duration elapsed = Duration.between(start, Instant.now());
        System.out.println("Completed " + TASK_COUNT + " tasks in " + elapsed.toMillis() + " ms");
        System.out.println("Only " + POOL_SIZE + " OS threads existed at once, so tasks queued up waiting for a free thread.");
    }
}
