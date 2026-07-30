package com.concurrency.lab.m05_explicit_locks;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockBasicsDemo {

    static class Counter {
        private final Lock lock = new ReentrantLock();
        private long value;

        void increment() {
            lock.lock();
            try {
                value++;
            } finally {
                lock.unlock();
            }
        }

        long get() {
            lock.lock();
            try {
                return value;
            } finally {
                lock.unlock();
            }
        }
    }

    static void tryLockDemo() throws InterruptedException {
        Lock lock = new ReentrantLock();
        CountDownLatch holderReady = new CountDownLatch(1);
        CountDownLatch releaseSignal = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                holderReady.countDown();
                releaseSignal.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "lock-holder");
        holder.start();
        holderReady.await();

        boolean acquiredQuickly = lock.tryLock();
        System.out.println("tryLock() while held (no wait): acquired=" + acquiredQuickly);

        boolean acquiredWithTimeout = lock.tryLock(200, TimeUnit.MILLISECONDS);
        System.out.println("tryLock(200ms) while still held: acquired=" + acquiredWithTimeout);

        releaseSignal.countDown();
        holder.join();

        boolean acquiredAfterRelease = lock.tryLock();
        System.out.println("tryLock() after release: acquired=" + acquiredAfterRelease);
        if (acquiredAfterRelease) {
            lock.unlock();
        }
    }

    private static long runContended(Lock lock, int threadCount, int incrementsPerThread) throws InterruptedException {
        long[] value = {0};
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < incrementsPerThread; i++) {
                        lock.lock();
                        try {
                            value[0]++;
                        } finally {
                            lock.unlock();
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

        System.out.println(lock.getClass().getSimpleName() + " -> final value=" + value[0]
                + ", elapsed=" + elapsedMillis + "ms");
        return elapsedMillis;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. Basic lock()/unlock() counter ==");
        Counter counter = new Counter();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                for (int j = 0; j < 10_000; j++) {
                    counter.increment();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("Expected 40000, got " + counter.get());

        System.out.println("== 2. tryLock() and tryLock(timeout) ==");
        tryLockDemo();

        System.out.println("== 3. Fair vs unfair ReentrantLock under contention ==");
        int threads = 8;
        int perThread = 20_000;
        System.out.println("-- Unfair (default) --");
        long unfairMillis = runContended(new ReentrantLock(false), threads, perThread);
        System.out.println("-- Fair --");
        long fairMillis = runContended(new ReentrantLock(true), threads, perThread);
        System.out.println("Unfair throughput advantage: fair took " + fairMillis + "ms vs unfair " + unfairMillis
                + "ms (fair locks pay for FIFO ordering, unfair locks favor a fresh acquirer -> less throughput lost to context switches)");
    }
}
