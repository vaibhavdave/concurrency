package com.concurrency.lab.m13_deadlock_livelock_starvation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class DeadlockFixTryLockBackoffDemo {

    private static final ReentrantLock lockA = new ReentrantLock();
    private static final ReentrantLock lockB = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        int iterations = 5_000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger totalRetries = new AtomicInteger();

        Thread threadOne = new Thread(() -> {
            awaitLatch(startLatch);
            for (int i = 0; i < iterations; i++) {
                totalRetries.addAndGet(acquireBothWithBackoff(lockA, lockB));
            }
            doneLatch.countDown();
        }, "tryLock-worker-A-then-B");

        Thread threadTwo = new Thread(() -> {
            awaitLatch(startLatch);
            for (int i = 0; i < iterations; i++) {
                totalRetries.addAndGet(acquireBothWithBackoff(lockB, lockA));
            }
            doneLatch.countDown();
        }, "tryLock-worker-B-then-A");

        threadOne.start();
        threadTwo.start();

        long start = System.nanoTime();
        startLatch.countDown();
        doneLatch.await();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Completed " + (iterations * 2) + " lock acquisitions across 2 threads requesting "
                + "opposite orders in " + elapsedMillis + " ms without deadlock");
        System.out.println("Total tryLock retries needed across both threads: " + totalRetries.get());
    }

    // tryLock with a timeout means a thread that cannot get the second lock simply
    // releases what it holds and retries after a random backoff, instead of blocking
    // forever -- this breaks the "hold and wait" precondition for deadlock
    private static int acquireBothWithBackoff(ReentrantLock first, ReentrantLock second) {
        int retries = 0;
        while (true) {
            boolean gotFirst = false;
            boolean gotSecond = false;
            try {
                gotFirst = first.tryLock(50, TimeUnit.MILLISECONDS);
                if (gotFirst) {
                    gotSecond = second.tryLock(50, TimeUnit.MILLISECONDS);
                    if (gotSecond) {
                        return retries;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return retries;
            } finally {
                if (gotSecond) {
                    second.unlock();
                }
                if (gotFirst) {
                    first.unlock();
                }
            }
            retries++;
            sleepQuietly(ThreadLocalRandom.current().nextInt(1, 5));
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
