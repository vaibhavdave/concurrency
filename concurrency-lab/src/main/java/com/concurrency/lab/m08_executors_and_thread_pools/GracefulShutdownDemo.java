package com.concurrency.lab.m08_executors_and_thread_pools;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GracefulShutdownDemo {

    private static void demonstrateShutdown() throws InterruptedException {
        System.out.println("== shutdown(): lets queued/running tasks finish, rejects new submissions ==");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> sleepQuietly(100, "shutdown-task"));
        pool.shutdown();
        boolean finished = pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("shutdown() finished within timeout: " + finished);
    }

    private static void demonstrateShutdownNow() throws InterruptedException {
        System.out.println("== shutdownNow(): interrupts running tasks, drains and returns unstarted ones ==");
        ExecutorService pool = Executors.newFixedThreadPool(1);
        pool.submit(() -> sleepQuietly(5000, "long-task"));
        pool.submit(() -> sleepQuietly(100, "queued-task"));
        Thread.sleep(50);
        List<Runnable> neverStarted = pool.shutdownNow();
        System.out.println("Tasks never started (still queued): " + neverStarted.size());
        boolean finished = pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("shutdownNow() finished within timeout: " + finished);
    }

    /**
     * Recommended shutdown sequence per the ExecutorService javadoc:
     * try graceful shutdown first, escalate to shutdownNow if it does not
     * complete in time, then wait once more for the forced interrupt to land.
     */
    private static void gracefulShutdownSequence(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                System.out.println("Timed out waiting for tasks; forcing shutdownNow()");
                pool.shutdownNow();
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.out.println("Pool did not terminate even after shutdownNow()");
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis, String label) {
        try {
            Thread.sleep(millis);
            System.out.println("[" + label + "] completed normally");
        } catch (InterruptedException e) {
            System.out.println("[" + label + "] interrupted before completion");
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        demonstrateShutdown();
        demonstrateShutdownNow();

        System.out.println("== correct shutdown sequence pattern ==");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> sleepQuietly(5000, "stubborn-task"));
        gracefulShutdownSequence(pool);
        System.out.println("Pool terminated: " + pool.isTerminated());
    }
}
