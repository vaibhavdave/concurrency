package com.concurrency.lab.m14_virtual_threads_structured_concurrency;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VirtualThreadsThroughputDemo {

    private static final int TASK_COUNT = 10_000;
    private static final long SIMULATED_IO_MILLIS = 50;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== Virtual-thread-per-task throughput ==");
        System.out.println("Submitting " + TASK_COUNT + " blocking tasks, one virtual thread each");

        CountDownLatch allDone = new CountDownLatch(TASK_COUNT);
        Instant start = Instant.now();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASK_COUNT; i++) {
                executor.submit(() -> {
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
        System.out.println("Virtual threads are cheap to create (thousands of them) and Thread.sleep()");
        System.out.println("unmounts the virtual thread from its carrier instead of blocking an OS thread,");
        System.out.println("so all 10,000 sleeps overlap instead of queueing behind a small pool.");
    }
}
