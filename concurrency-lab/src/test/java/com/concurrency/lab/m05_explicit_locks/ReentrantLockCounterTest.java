package com.concurrency.lab.m05_explicit_locks;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

class ReentrantLockCounterTest {

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

    @Test
    void concurrentIncrementsProduceExactExpectedTotal() throws InterruptedException {
        Counter counter = new Counter();
        int threadCount = 8;
        int incrementsPerThread = 5_000;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        counter.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(counter.get()).isEqualTo((long) threadCount * incrementsPerThread);
    }

    @Test
    void tryLockFailsWhileAnotherThreadHoldsTheLockThenSucceedsAfterRelease() throws InterruptedException {
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
        });
        holder.start();

        assertThat(holderReady.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(lock.tryLock()).isFalse();

        releaseSignal.countDown();
        holder.join(2000);

        assertThat(lock.tryLock(2, TimeUnit.SECONDS)).isTrue();
        lock.unlock();
    }
}
