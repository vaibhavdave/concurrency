package com.concurrency.lab.m11_fork_join_and_parallel_streams;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class CommonPoolStarvationDemo {

    private static final int BLOCKING_TASK_COUNT = Runtime.getRuntime().availableProcessors() * 4;
    private static final long BLOCKING_SLEEP_MS = 300;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("commonPool parallelism = " + ForkJoinPool.commonPool().getParallelism());

        System.out.println();
        System.out.println("== Problem: blocking work saturates ForkJoinPool.commonPool() ==");
        runStarvationScenario();

        System.out.println();
        System.out.println("== Fix: run the blocking parallel stream on a dedicated pool ==");
        runFixedScenario();
    }

    private static void runStarvationScenario() throws InterruptedException {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        Thread blocker = new Thread(() -> {
            blockerStarted.countDown();
            IntStream.range(0, BLOCKING_TASK_COUNT).parallel().forEach(i -> sleepQuietly(BLOCKING_SLEEP_MS));
        }, "commonpool-blocker");
        blocker.start();
        blockerStarted.await();
        Thread.sleep(20); // let the blocker's tasks actually land on commonPool workers

        long start = System.nanoTime();
        long unrelatedSum = IntStream.range(0, 20).parallel()
                .mapToLong(i -> i)
                .sum();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("unrelated parallel stream (sum=" + unrelatedSum + ") took " + elapsedMillis
                + " ms while commonPool was saturated with blocking work"
                + (elapsedMillis > BLOCKING_SLEEP_MS / 2 ? "  <-- starved, delayed by the blockers" : ""));

        blocker.join();
    }

    private static void runFixedScenario() throws InterruptedException {
        ForkJoinPool dedicatedPool = new ForkJoinPool(BLOCKING_TASK_COUNT);
        try {
            CountDownLatch blockerStarted = new CountDownLatch(1);
            Thread blocker = new Thread(() -> {
                blockerStarted.countDown();
                try {
                    dedicatedPool.submit(() ->
                            IntStream.range(0, BLOCKING_TASK_COUNT).parallel()
                                    .forEach(i -> sleepQuietly(BLOCKING_SLEEP_MS))
                    ).get();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            }, "dedicated-pool-blocker");
            blocker.start();
            blockerStarted.await();
            Thread.sleep(20);

            long start = System.nanoTime();
            long unrelatedSum = IntStream.range(0, 20).parallel()
                    .mapToLong(i -> i)
                    .sum();
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            System.out.println("unrelated parallel stream (sum=" + unrelatedSum + ") took " + elapsedMillis
                    + " ms while blocking work ran on its own dedicated pool"
                    + (elapsedMillis < BLOCKING_SLEEP_MS / 2 ? "  <-- commonPool stayed free" : ""));

            blocker.join();
        } finally {
            dedicatedPool.shutdown();
            dedicatedPool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
