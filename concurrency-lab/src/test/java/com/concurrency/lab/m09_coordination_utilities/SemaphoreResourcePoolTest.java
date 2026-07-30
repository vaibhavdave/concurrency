package com.concurrency.lab.m09_coordination_utilities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class SemaphoreResourcePoolTest {

    @Test
    void neverExceedsAvailablePermitsUnderConcurrentDemand() throws InterruptedException {
        int permitCount = 2;
        int clientCount = 8;
        Semaphore semaphore = new Semaphore(permitCount, true);
        AtomicInteger concurrentUsers = new AtomicInteger();
        AtomicInteger maxObservedConcurrency = new AtomicInteger();
        CountDownLatch allDone = new CountDownLatch(clientCount);

        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        for (int i = 0; i < clientCount; i++) {
            pool.submit(() -> {
                try {
                    semaphore.acquire();
                    try {
                        int current = concurrentUsers.incrementAndGet();
                        maxObservedConcurrency.updateAndGet(max -> Math.max(max, current));
                        Thread.sleep(50);
                        concurrentUsers.decrementAndGet();
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            });
        }

        assertThat(allDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(maxObservedConcurrency.get()).isLessThanOrEqualTo(permitCount);
        assertThat(semaphore.availablePermits()).isEqualTo(permitCount);

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void waitingAcquirerIsReleasedOnlyAfterAPermitIsFreed() throws InterruptedException {
        Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();

        AtomicInteger acquiredCount = new AtomicInteger();
        Thread waiter = new Thread(() -> {
            try {
                semaphore.acquire();
                acquiredCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> semaphore.getQueueLength() == 1);
        assertThat(acquiredCount.get()).isZero();

        semaphore.release();
        waiter.join(2000);

        assertThat(acquiredCount.get()).isEqualTo(1);
    }
}
