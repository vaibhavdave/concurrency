package com.concurrency.lab.m03_java_memory_model_and_volatile;

import java.util.concurrent.CountDownLatch;

public class FalseSharingDemo {

    private static final long ITERATIONS = 500_000_000L;

    static class PackedCounters {
        volatile long counter1;
        volatile long counter2;
    }

    static class PaddedCounters {
        volatile long counter1;
        // Padding pushes counter2 onto a different 64-byte cache line than
        // counter1, so the two threads stop invalidating each other's cache
        // line on every write (false sharing).
        long p1, p2, p3, p4, p5, p6, p7;
        volatile long counter2;
    }

    public static void main(String[] args) throws InterruptedException {
        long packedMs = timePacked();
        long paddedMs = timePadded();

        System.out.println();
        System.out.println("Packed (false sharing) : " + packedMs + " ms");
        System.out.println("Padded (no false sharing): " + paddedMs + " ms");
        System.out.println("(Absolute numbers are machine/JIT/core-topology dependent; what matters "
                + "is the relative difference between the two runs.)");
    }

    private static long timePacked() throws InterruptedException {
        PackedCounters counters = new PackedCounters();
        return timeTwoThreadsIncrementing(
                () -> counters.counter1++,
                () -> counters.counter2++);
    }

    private static long timePadded() throws InterruptedException {
        PaddedCounters counters = new PaddedCounters();
        return timeTwoThreadsIncrementing(
                () -> counters.counter1++,
                () -> counters.counter2++);
    }

    private static long timeTwoThreadsIncrementing(Runnable incrementFirst, Runnable incrementSecond)
            throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            awaitQuietly(startGate);
            for (long i = 0; i < ITERATIONS; i++) {
                incrementFirst.run();
            }
            doneLatch.countDown();
        }, "counter1-thread");

        Thread t2 = new Thread(() -> {
            awaitQuietly(startGate);
            for (long i = 0; i < ITERATIONS; i++) {
                incrementSecond.run();
            }
            doneLatch.countDown();
        }, "counter2-thread");

        long start = System.nanoTime();
        t1.start();
        t2.start();
        startGate.countDown();
        doneLatch.await();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
