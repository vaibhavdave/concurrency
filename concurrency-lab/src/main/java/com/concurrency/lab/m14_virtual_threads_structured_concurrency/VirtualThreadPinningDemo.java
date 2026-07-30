package com.concurrency.lab.m14_virtual_threads_structured_concurrency;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class VirtualThreadPinningDemo {

    private static final Object MONITOR = new Object();
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final long HOLD_MILLIS = 100;
    private static final int TASK_COUNT = 50;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== Virtual thread pinning: synchronized vs ReentrantLock ==");
        System.out.println("Run this with -Djdk.tracePinnedThreads=full to see \"Thread pinned\" stack traces");
        System.out.println("for the synchronized-block run and NOT for the ReentrantLock run.\n");

        System.out.println("-- synchronized block (blocks while holding a monitor: PINS the carrier) --");
        runWithSynchronized();

        System.out.println("\n-- ReentrantLock (blocks while holding a lock: does NOT pin the carrier) --");
        runWithReentrantLock();

        System.out.println("\nConceptually: when a virtual thread parks (e.g. Thread.sleep, blocking I/O) while");
        System.out.println("holding a Java monitor entered via 'synchronized', the JVM cannot unmount it from");
        System.out.println("its carrier platform thread, because releasing/reacquiring monitor state across an");
        System.out.println("unmount is not supported by the current monitor implementation. The carrier is");
        System.out.println("pinned for the duration of the blocking call, which can starve other virtual threads");
        System.out.println("if the carrier pool (ForkJoinPool.commonPool by default) is small.");
        System.out.println("java.util.concurrent.locks.ReentrantLock (and other j.u.c locks) are implemented to");
        System.out.println("cooperate with the virtual thread scheduler, so blocking while holding one unmounts");
        System.out.println("the virtual thread from its carrier just like any other blocking operation, freeing");
        System.out.println("the carrier to run other virtual threads.");
    }

    private static void runWithSynchronized() throws InterruptedException {
        Instant start = Instant.now();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASK_COUNT; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    synchronized (MONITOR) {
                        blockBriefly(taskId, "synchronized");
                    }
                });
            }
        }
        System.out.println("synchronized run took " + Duration.between(start, Instant.now()).toMillis() + " ms"
                + " (serialized because every task waits for the monitor AND its carrier while pinned)");
    }

    private static void runWithReentrantLock() throws InterruptedException {
        Instant start = Instant.now();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASK_COUNT; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    LOCK.lock();
                    try {
                        blockBriefly(taskId, "ReentrantLock");
                    } finally {
                        LOCK.unlock();
                    }
                });
            }
        }
        System.out.println("ReentrantLock run took " + Duration.between(start, Instant.now()).toMillis() + " ms"
                + " (still serialized by the lock itself, but carriers are free to serve other virtual threads meanwhile)");
    }

    private static void blockBriefly(int taskId, String guard) {
        try {
            Thread.sleep(HOLD_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (taskId == 0) {
            System.out.println("[" + guard + "] task " + taskId + " held the guard for " + HOLD_MILLIS + " ms on "
                    + Thread.currentThread());
        }
    }
}
