package com.concurrency.lab.m06_concurrent_collections;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrentSkipListMapDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. Concurrent inserts from multiple threads land in sorted order ==");
        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap<>();
        int threadCount = 8;
        int keysPerThread = 200;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            int threadIndex = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < keysPerThread; i++) {
                        int key = threadIndex * keysPerThread + i;
                        map.put(key, "value-" + key);
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

        System.out.println("Total keys inserted=" + map.size() + " (expected " + threadCount * keysPerThread + ")");
        System.out.println("firstKey=" + map.firstKey() + ", lastKey=" + map.lastKey());
        System.out.println("Keys are always retrievable in ascending order regardless of insertion order,");
        System.out.println("because the underlying skip list keeps itself sorted with lock-free CAS operations.");

        System.out.println("== 2. Range-view queries ==");
        Map<Integer, String> head = map.headMap(50);
        Map<Integer, String> tail = map.tailMap(1550);
        Map<Integer, String> sub = map.subMap(100, 110);

        System.out.println("headMap(50) size=" + head.size() + " (keys strictly less than 50)");
        System.out.println("tailMap(1550) size=" + tail.size() + " (keys >= 1550)");
        System.out.println("subMap(100, 110) keys=" + sub.keySet() + " (100 inclusive .. 110 exclusive)");
        System.out.println("Range views are live windows backed by the same map, not copies.");
    }
}
