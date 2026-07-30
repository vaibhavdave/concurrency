package com.concurrency.lab.m09_coordination_utilities;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CountDownLatchDemo {

    private static void demonstrateStartupLatch() throws InterruptedException {
        System.out.println("== startup latch: coordinator waits for all workers to be ready ==");
        int workerCount = 4;
        CountDownLatch readyLatch = new CountDownLatch(workerCount);
        ExecutorService pool = Executors.newFixedThreadPool(workerCount);

        for (int i = 0; i < workerCount; i++) {
            int workerId = i;
            pool.submit(() -> {
                System.out.println("[worker-" + workerId + "] initializing");
                sleepQuietly(50L * (workerId + 1));
                System.out.println("[worker-" + workerId + "] ready");
                readyLatch.countDown();
            });
        }

        readyLatch.await();
        System.out.println("[coordinator] all " + workerCount + " workers ready, proceeding");
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void demonstrateCompletionLatch() throws InterruptedException {
        System.out.println("== completion latch: wait for all workers to finish their job ==");
        int workerCount = 4;
        CountDownLatch doneLatch = new CountDownLatch(workerCount);
        ExecutorService pool = Executors.newFixedThreadPool(workerCount);

        for (int i = 0; i < workerCount; i++) {
            int workerId = i;
            pool.submit(() -> {
                sleepQuietly(30L * (workerId + 1));
                System.out.println("[worker-" + workerId + "] finished work");
                doneLatch.countDown();
            });
        }

        doneLatch.await();
        System.out.println("[coordinator] all workers finished");
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        demonstrateStartupLatch();
        demonstrateCompletionLatch();
    }
}
