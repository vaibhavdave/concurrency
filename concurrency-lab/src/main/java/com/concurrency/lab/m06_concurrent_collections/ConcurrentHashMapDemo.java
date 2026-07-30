package com.concurrency.lab.m06_concurrent_collections;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrentHashMapDemo {

    static void atomicCompoundOperations() {
        ConcurrentHashMap<String, Integer> wordCounts = new ConcurrentHashMap<>();

        wordCounts.putIfAbsent("apple", 1);
        wordCounts.putIfAbsent("apple", 99);
        System.out.println("After two putIfAbsent(\"apple\", ...): " + wordCounts.get("apple") + " (second call ignored)");

        wordCounts.computeIfAbsent("banana", k -> 0);
        System.out.println("computeIfAbsent(\"banana\", -> 0): " + wordCounts.get("banana"));

        wordCounts.compute("apple", (k, v) -> v == null ? 1 : v + 1);
        System.out.println("compute(\"apple\", v -> v+1): " + wordCounts.get("apple"));

        wordCounts.merge("apple", 1, Integer::sum);
        wordCounts.merge("cherry", 1, Integer::sum);
        System.out.println("merge(\"apple\", 1, sum): " + wordCounts.get("apple"));
        System.out.println("merge(\"cherry\", 1, sum) on absent key: " + wordCounts.get("cherry"));
        System.out.println("Each of these is a single atomic compound operation under the map's internal locking,");
        System.out.println("unlike separate get()-then-put() calls which can race between threads.");
    }

    // each thread owns a distinct key, so a real ConcurrentHashMap can service them via different
    // bins/locks in parallel; synchronizedMap serializes all of them behind one monitor regardless
    private static long runContendedIncrement(Map<String, Integer> map, int threadCount, int incrementsPerThread)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            String key = "counter-" + t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < incrementsPerThread; i++) {
                        if (map instanceof ConcurrentHashMap) {
                            ((ConcurrentHashMap<String, Integer>) map).merge(key, 1, Integer::sum);
                        } else {
                            synchronized (map) {
                                map.merge(key, 1, Integer::sum);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        long begin = System.nanoTime();
        start.countDown();
        done.await();
        long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;
        pool.shutdown();

        int total = map.values().stream().mapToInt(Integer::intValue).sum();
        int expected = threadCount * incrementsPerThread;
        System.out.println(map.getClass().getSimpleName() + " -> total=" + total
                + " (expected " + expected + "), elapsed=" + elapsedMillis + "ms");
        return elapsedMillis;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. Atomic compound operations ==");
        atomicCompoundOperations();

        System.out.println("== 2. Throughput: ConcurrentHashMap vs Collections.synchronizedMap under contention ==");
        int threads = 8;
        int perThread = 50_000;

        long chmMillis = runContendedIncrement(new ConcurrentHashMap<>(), threads, perThread);
        long syncMillis = runContendedIncrement(Collections.synchronizedMap(new HashMap<>()), threads, perThread);

        System.out.println("ConcurrentHashMap (" + chmMillis + "ms) uses lock striping / CAS on bins so writers to");
        System.out.println("different keys rarely contend; synchronizedMap (" + syncMillis
                + "ms) serializes every access behind one monitor lock.");
    }
}
