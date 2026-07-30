package com.concurrency.lab.m04_atomic_and_cas;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicCounterTest {

    @Test
    void incrementAndGetIsAtomicUnderContention() throws InterruptedException {
        int threadCount = 16;
        int incrementsPerThread = 10_000;
        AtomicInteger counter = new AtomicInteger(0);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        counter.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startGate.countDown();
        doneLatch.await();

        assertThat(counter.get()).isEqualTo(threadCount * incrementsPerThread);
    }

    @Test
    void manualCasRetryLoopDoublesValueUnderContention() throws InterruptedException {
        AtomicInteger value = new AtomicInteger(1);
        int threadCount = 10;
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                int oldValue;
                int newValue;
                do {
                    oldValue = value.get();
                    newValue = oldValue + 1;
                } while (!value.compareAndSet(oldValue, newValue));
                doneLatch.countDown();
            }).start();
        }

        doneLatch.await();
        assertThat(value.get()).isEqualTo(1 + threadCount);
    }

    @Test
    void getAndUpdateReturnsPreviousValue() {
        AtomicInteger value = new AtomicInteger(5);
        int previous = value.getAndUpdate(v -> v * 10);
        assertThat(previous).isEqualTo(5);
        assertThat(value.get()).isEqualTo(50);
    }
}
