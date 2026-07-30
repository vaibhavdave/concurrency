package com.concurrency.lab.m05_explicit_locks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadWriteLockDemo {

    static class Cache<K, V> {
        private final Map<K, V> data = new HashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        V get(K key, long simulatedReadMillis) {
            lock.readLock().lock();
            try {
                sleepQuietly(simulatedReadMillis);
                return data.get(key);
            } finally {
                lock.readLock().unlock();
            }
        }

        void put(K key, V value, long simulatedWriteMillis) {
            lock.writeLock().lock();
            try {
                sleepQuietly(simulatedWriteMillis);
                data.put(key, value);
            } finally {
                lock.writeLock().unlock();
            }
        }

        private static void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Cache<String, String> cache = new Cache<>();
        cache.put("k", "initial", 0);

        System.out.println("== 1. Many concurrent readers proceed in parallel ==");
        int readerCount = 8;
        long perReadMillis = 100;
        ExecutorService readerPool = Executors.newFixedThreadPool(readerCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readerCount);

        long begin = System.nanoTime();
        for (int i = 0; i < readerCount; i++) {
            readerPool.submit(() -> {
                try {
                    start.await();
                    cache.get("k", perReadMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        long readersElapsedMillis = (System.nanoTime() - begin) / 1_000_000;
        readerPool.shutdown();

        System.out.println(readerCount + " readers x " + perReadMillis + "ms each finished in "
                + readersElapsedMillis + "ms (would be ~" + (readerCount * perReadMillis)
                + "ms if serialized -> readers ran in parallel)");

        System.out.println("== 2. A writer gets exclusive access ==");
        Thread writer = new Thread(() -> cache.put("k", "updated-by-writer", 150), "writer");
        Thread readerDuringWrite = new Thread(() -> {
            long readBegin = System.nanoTime();
            String value = cache.get("k", 0);
            long readElapsedMillis = (System.nanoTime() - readBegin) / 1_000_000;
            System.out.println("[reader] read '" + value + "' after waiting " + readElapsedMillis
                    + "ms for the writer's exclusive lock to release");
        }, "reader-during-write");

        writer.start();
        Thread.sleep(30);
        readerDuringWrite.start();
        writer.join();
        readerDuringWrite.join();

        System.out.println("Final value=" + cache.get("k", 0));
    }
}
