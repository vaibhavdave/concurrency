package com.concurrency.lab.m02_race_conditions_and_synchronized;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

class SafeSynchronizedCounterTest {

    private static final int THREAD_COUNT = 16;
    private static final int INCREMENTS_PER_THREAD = 10_000;

    @Test
    void countsCorrectlyUnderConcurrentIncrements() throws InterruptedException {
        SafeSynchronizedCounter counter = new SafeSynchronizedCounter();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        Thread[] threads = new Thread[THREAD_COUNT];

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                        counter.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            threads[i].start();
        }

        startGate.countDown();
        doneLatch.await();
        for (Thread t : threads) {
            t.join();
        }

        assertThat(counter.get()).isEqualTo(THREAD_COUNT * INCREMENTS_PER_THREAD);
    }
}
