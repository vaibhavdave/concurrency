package com.concurrency.lab.m02_race_conditions_and_synchronized;

import java.util.concurrent.CountDownLatch;

public class RaceConditionDemo {

    private static final int THREAD_COUNT = 10;
    private static final int INCREMENTS_PER_THREAD = 100_000;

    public static void main(String[] args) throws InterruptedException {
        int expected = THREAD_COUNT * INCREMENTS_PER_THREAD;

        System.out.println("== UnsafeCounter (expected " + expected + ") ==");
        UnsafeCounter unsafeCounter = new UnsafeCounter();
        runConcurrently(unsafeCounter::increment);
        System.out.println("UnsafeCounter final value = " + unsafeCounter.get()
                + (unsafeCounter.get() < expected ? "  <-- lost updates!" : ""));

        System.out.println("== SafeSynchronizedCounter (expected " + expected + ") ==");
        SafeSynchronizedCounter safeCounter = new SafeSynchronizedCounter();
        runConcurrently(safeCounter::increment);
        System.out.println("SafeSynchronizedCounter final value = " + safeCounter.get());
    }

    private static void runConcurrently(Runnable incrementAction) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        Thread[] threads = new Thread[THREAD_COUNT];

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
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

        startGate.countDown();
        doneLatch.await();
    }
}
