package com.concurrency.lab.m08_executors_and_thread_pools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExecutorTypesDemo {

    private static void runWorkload(String label, ExecutorService pool, int tasks) throws InterruptedException {
        System.out.println("== " + label + " ==");
        for (int i = 0; i < tasks; i++) {
            int taskId = i;
            pool.submit(() -> {
                System.out.println("[" + label + "] task " + taskId + " on " + Thread.currentThread().getName());
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws InterruptedException {
        // fixed pool: bounded thread count, unbounded queue -> tasks queue up rather than growing threads
        runWorkload("newFixedThreadPool(3)", Executors.newFixedThreadPool(3), 6);

        // cached pool: no upper bound, threads created on demand and reused/reaped after 60s idle
        runWorkload("newCachedThreadPool()", Executors.newCachedThreadPool(), 6);

        // single-thread executor: strict FIFO, one task at a time, guarantees no concurrent execution
        runWorkload("newSingleThreadExecutor()", Executors.newSingleThreadExecutor(), 3);

        System.out.println("== newScheduledThreadPool(2) ==");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.schedule(() -> System.out.println("[scheduled] one-shot task after 100ms"), 100, TimeUnit.MILLISECONDS);
        Thread.sleep(200);
        scheduler.shutdown();
        scheduler.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("All executor types demonstrated.");
    }
}
