package com.concurrency.lab.m09_coordination_utilities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CountDownLatchTest {

    @Test
    void startupLatchReleasesCoordinatorOnlyAfterAllWorkersSignalReady() throws InterruptedException {
        int workerCount = 5;
        CountDownLatch readyLatch = new CountDownLatch(workerCount);
        AtomicInteger readyBeforeAwaitReturns = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(workerCount);

        for (int i = 0; i < workerCount; i++) {
            pool.submit(() -> {
                readyBeforeAwaitReturns.incrementAndGet();
                readyLatch.countDown();
            });
        }

        boolean releasedInTime = readyLatch.await(5, TimeUnit.SECONDS);

        assertThat(releasedInTime).isTrue();
        assertThat(readyLatch.getCount()).isZero();
        assertThat(readyBeforeAwaitReturns.get()).isEqualTo(workerCount);

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void awaitTimesOutWhenNotAllWorkersCountDown() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        latch.countDown();

        boolean released = latch.await(200, TimeUnit.MILLISECONDS);

        assertThat(released).isFalse();
        assertThat(latch.getCount()).isEqualTo(1);
    }
}
