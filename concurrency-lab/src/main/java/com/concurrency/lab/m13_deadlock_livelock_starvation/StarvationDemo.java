package com.concurrency.lab.m13_deadlock_livelock_starvation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StarvationDemo {

    private static final int GREEDY_THREAD_COUNT = 6;
    private static final long RUN_MILLIS = 1500;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== Unfair lock: an unlucky thread can be starved ==");
        runScenario(new ReentrantLock(false), "unfair");

        System.out.println();
        System.out.println("== Fair lock: new ReentrantLock(true) eventually serves everyone ==");
        runScenario(new ReentrantLock(true), "fair");
    }

    private static void runScenario(Lock lock, String label) throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(true);
        Map<String, AtomicLong> acquisitionCounts = new ConcurrentHashMap<>();
        Map<String, AtomicLong> totalWaitNanos = new ConcurrentHashMap<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(GREEDY_THREAD_COUNT + 1);

        Thread[] greedyThreads = new Thread[GREEDY_THREAD_COUNT];
        for (int t = 0; t < GREEDY_THREAD_COUNT; t++) {
            String name = "greedy-" + t;
            acquisitionCounts.put(name, new AtomicLong());
            totalWaitNanos.put(name, new AtomicLong());
            greedyThreads[t] = new Thread(() -> workLoop(lock, name, running, startLatch, doneLatch,
                    acquisitionCounts, totalWaitNanos), name);
        }

        String unluckyName = "unlucky";
        acquisitionCounts.put(unluckyName, new AtomicLong());
        totalWaitNanos.put(unluckyName, new AtomicLong());
        Thread unluckyThread = new Thread(() -> workLoop(lock, unluckyName, running, startLatch, doneLatch,
                acquisitionCounts, totalWaitNanos), unluckyName);

        for (Thread thread : greedyThreads) {
            thread.start();
        }
        unluckyThread.start();
        startLatch.countDown();

        Thread.sleep(RUN_MILLIS);
        running.set(false);
        doneLatch.await();

        System.out.printf("[%s] %-10s acquisitions=%-8d avgWaitMicros=%.1f%n", label, unluckyName,
                acquisitionCounts.get(unluckyName).get(),
                averageWaitMicros(acquisitionCounts.get(unluckyName), totalWaitNanos.get(unluckyName)));
        long totalGreedyAcquisitions = 0;
        for (int t = 0; t < GREEDY_THREAD_COUNT; t++) {
            totalGreedyAcquisitions += acquisitionCounts.get("greedy-" + t).get();
        }
        System.out.printf("[%s] greedy threads combined acquisitions=%d%n", label, totalGreedyAcquisitions);
    }

    private static void workLoop(Lock lock, String name, AtomicBoolean running, CountDownLatch startLatch,
                                  CountDownLatch doneLatch, Map<String, AtomicLong> acquisitionCounts,
                                  Map<String, AtomicLong> totalWaitNanos) {
        try {
            startLatch.await();
            while (running.get()) {
                long waitStart = System.nanoTime();
                lock.lock();
                long waitNanos = System.nanoTime() - waitStart;
                try {
                    totalWaitNanos.get(name).addAndGet(waitNanos);
                    acquisitionCounts.get(name).incrementAndGet();
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            doneLatch.countDown();
        }
    }

    private static double averageWaitMicros(AtomicLong count, AtomicLong totalNanos) {
        long c = count.get();
        return c == 0 ? 0 : (totalNanos.get() / 1000.0) / c;
    }
}
