package com.concurrency.lab.m04_atomic_and_cas;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class LongAdderVsAtomicLongDemo {

    private static final int THREAD_COUNT = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
    private static final long INCREMENTS_PER_THREAD = 2_000_000L;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Using " + THREAD_COUNT + " contending threads");

        long atomicLongMs = timeAtomicLong();
        long longAdderMs = timeLongAdder();

        System.out.println();
        System.out.println("AtomicLong : " + atomicLongMs + " ms");
        System.out.println("LongAdder  : " + longAdderMs + " ms");
        System.out.println("Under high contention, LongAdder is typically faster: writes go to per-thread "
                + "striped Cells that are combined only when sum() is called, so threads mostly avoid "
                + "CAS-retrying against the same memory location. AtomicLong is typically better for "
                + "low-contention counters or when you need to read the exact value after every single "
                + "update, since sum() on LongAdder is only an eventually-consistent snapshot under "
                + "concurrent updates.");
    }

    private static long timeAtomicLong() throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        long elapsed = timeConcurrent(counter::incrementAndGet);
        System.out.println("AtomicLong final value = " + counter.get());
        return elapsed;
    }

    private static long timeLongAdder() throws InterruptedException {
        LongAdder counter = new LongAdder();
        long elapsed = timeConcurrent(counter::increment);
        System.out.println("LongAdder final value = " + counter.sum());
        return elapsed;
    }

    private static long timeConcurrent(Runnable incrementAction) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        Thread[] threads = new Thread[THREAD_COUNT];

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startGate.await();
                    for (long j = 0; j < INCREMENTS_PER_THREAD; j++) {
                        incrementAction.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            threads[i].start();
        }

        long start = System.nanoTime();
        startGate.countDown();
        doneLatch.await();
        return (System.nanoTime() - start) / 1_000_000;
    }
}
