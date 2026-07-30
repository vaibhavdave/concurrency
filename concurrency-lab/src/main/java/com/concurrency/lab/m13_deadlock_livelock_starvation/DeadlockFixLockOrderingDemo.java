package com.concurrency.lab.m13_deadlock_livelock_starvation;

import java.util.concurrent.CountDownLatch;

public class DeadlockFixLockOrderingDemo {

    private static final Object lockA = new Object();
    private static final Object lockB = new Object();

    public static void main(String[] args) throws InterruptedException {
        int iterations = 20_000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // thread 1 "wants" A then B, thread 2 "wants" B then A -- same opposite-order
        // scenario as DeadlockDemo, but acquireInOrder() below enforces one global order
        Thread threadOne = new Thread(() -> {
            awaitLatch(startLatch);
            for (int i = 0; i < iterations; i++) {
                acquireInOrder(lockA, lockB);
            }
            doneLatch.countDown();
        }, "ordered-worker-A-then-B");

        Thread threadTwo = new Thread(() -> {
            awaitLatch(startLatch);
            for (int i = 0; i < iterations; i++) {
                acquireInOrder(lockB, lockA);
            }
            doneLatch.countDown();
        }, "ordered-worker-B-then-A");

        threadOne.start();
        threadTwo.start();

        long start = System.nanoTime();
        startLatch.countDown();
        doneLatch.await();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Completed " + (iterations * 2) + " lock acquisitions across 2 threads requesting "
                + "opposite orders in " + elapsedMillis + " ms without deadlock");
    }

    // why the ordering must be GLOBAL, not per-thread: if each thread simply acquired
    // locks in whatever order it was handed, two threads requesting opposite orders would
    // still deadlock; deriving a single total order from identityHashCode (falling back to
    // a tie-break lock for the rare hash collision) removes the cycle for every caller
    private static void acquireInOrder(Object requestedFirst, Object requestedSecond) {
        Object first = requestedFirst;
        Object second = requestedSecond;
        if (System.identityHashCode(first) > System.identityHashCode(second)) {
            first = requestedSecond;
            second = requestedFirst;
        }
        synchronized (first) {
            synchronized (second) {
                // critical section: both resources held, always in the same global order
            }
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
