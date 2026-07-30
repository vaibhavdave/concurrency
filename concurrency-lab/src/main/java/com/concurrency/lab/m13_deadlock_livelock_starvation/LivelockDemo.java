package com.concurrency.lab.m13_deadlock_livelock_starvation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LivelockDemo {

    private static final int MAX_ATTEMPTS = 20;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== Livelock: two polite threads that always yield to each other ==");
        runLivelockingVersion();

        System.out.println();
        System.out.println("== Fix: random jittered backoff breaks the symmetry ==");
        runJitteredFixVersion();
    }

    private static void runLivelockingVersion() throws InterruptedException {
        AtomicBoolean resourceWantedByOne = new AtomicBoolean(false);
        AtomicBoolean resourceWantedByTwo = new AtomicBoolean(false);
        AtomicBoolean progressMade = new AtomicBoolean(false);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Thread politeOne = politeWorker("polite-one", resourceWantedByOne, resourceWantedByTwo,
                progressMade, startLatch, doneLatch, false);
        Thread politeTwo = politeWorker("polite-two", resourceWantedByTwo, resourceWantedByOne,
                progressMade, startLatch, doneLatch, false);

        politeOne.start();
        politeTwo.start();
        startLatch.countDown();
        doneLatch.await();

        if (!progressMade.get()) {
            System.out.println("no progress made after " + MAX_ATTEMPTS + " attempts");
        }
    }

    private static void runJitteredFixVersion() throws InterruptedException {
        AtomicBoolean resourceWantedByOne = new AtomicBoolean(false);
        AtomicBoolean resourceWantedByTwo = new AtomicBoolean(false);
        AtomicBoolean progressMade = new AtomicBoolean(false);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Thread jitteredOne = politeWorker("jittered-one", resourceWantedByOne, resourceWantedByTwo,
                progressMade, startLatch, doneLatch, true);
        Thread jitteredTwo = politeWorker("jittered-two", resourceWantedByTwo, resourceWantedByOne,
                progressMade, startLatch, doneLatch, true);

        jitteredOne.start();
        jitteredTwo.start();
        startLatch.countDown();
        doneLatch.await();

        System.out.println("progress made = " + progressMade.get());
    }

    private static Thread politeWorker(String name, AtomicBoolean mine, AtomicBoolean theirs,
                                        AtomicBoolean progressMade, CountDownLatch startLatch,
                                        CountDownLatch doneLatch, boolean useJitter) {
        return new Thread(() -> {
            awaitLatch(startLatch);
            AtomicInteger attempts = new AtomicInteger();
            mine.set(true);
            try {
                while (attempts.get() < MAX_ATTEMPTS && !progressMade.get()) {
                    attempts.incrementAndGet();
                    if (theirs.get()) {
                        // "polite": back off if the other side also wants the resource
                        mine.set(false);
                        if (useJitter) {
                            // why jitter breaks the symmetry: without it both threads back off
                            // and retry on the exact same cadence, so they collide forever;
                            // a random delay makes one of them arrive first almost every time
                            sleepQuietly(ThreadLocalRandom.current().nextInt(1, 10));
                        } else {
                            sleepQuietly(5);
                        }
                        mine.set(true);
                        continue;
                    }
                    progressMade.set(true);
                }
            } finally {
                doneLatch.countDown();
            }
        }, name);
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
