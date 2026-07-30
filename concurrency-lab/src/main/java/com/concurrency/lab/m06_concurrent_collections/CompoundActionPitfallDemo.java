package com.concurrency.lab.m06_concurrent_collections;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CompoundActionPitfallDemo {

    private static int racyCheckThenAct(int threadCount, int attemptsPerThread) throws InterruptedException {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        AtomicInteger putCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        // NOT atomic: another thread can slip a put() in between containsKey() and put(),
                        // so more than one thread can observe "absent" and both proceed to write
                        if (!map.containsKey("shared-key")) {
                            map.put("shared-key", 1);
                            putCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
        return putCount.get();
    }

    private static int correctPutIfAbsent(int threadCount, int attemptsPerThread) throws InterruptedException {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        AtomicInteger putCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (map.putIfAbsent("shared-key", 1) == null) {
                            putCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
        return putCount.get();
    }

    public static void main(String[] args) throws Exception {
        int threadCount = 16;
        int attemptsPerThread = 20_000;

        System.out.println("== 1. Racy 'if (!map.containsKey(k)) map.put(k, v)' ==");
        int racyWinners = 0;
        for (int trial = 0; trial < 5; trial++) {
            int winners = racyCheckThenAct(threadCount, attemptsPerThread);
            System.out.println("Trial " + trial + ": " + winners + " thread(s) believed they were first to insert"
                    + (winners > 1 ? "  <-- RACE DETECTED" : ""));
            racyWinners = Math.max(racyWinners, winners);
        }
        System.out.println("Expected exactly 1 winner if this were atomic; observed up to " + racyWinners
                + " across trials. containsKey() and put() are each atomic individually, but the combination");
        System.out.println("is not -- a thread can be preempted between the read and the write.");

        System.out.println("== 2. Fixed with putIfAbsent() (single atomic compound operation) ==");
        for (int trial = 0; trial < 5; trial++) {
            int winners = correctPutIfAbsent(threadCount, attemptsPerThread);
            System.out.println("Trial " + trial + ": " + winners + " thread(s) won the insert (always exactly 1)");
        }

        System.out.println("computeIfAbsent() gives the same atomicity guarantee when the value must be computed");
        System.out.println("lazily: map.computeIfAbsent(key, k -> expensiveInit()) runs the mapping function at");
        System.out.println("most once per key even under contention.");
    }
}
