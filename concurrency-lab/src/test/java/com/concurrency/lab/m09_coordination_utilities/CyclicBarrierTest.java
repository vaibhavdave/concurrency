package com.concurrency.lab.m09_coordination_utilities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CyclicBarrierTest {

    @Test
    void barrierActionRunsExactlyOncePerRoundAfterAllPartiesArrive() throws Exception {
        int parties = 3;
        int rounds = 2;
        AtomicInteger barrierActionRuns = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(parties, barrierActionRuns::incrementAndGet);
        CountDownLatch allWorkersDone = new CountDownLatch(parties);

        ExecutorService pool = Executors.newFixedThreadPool(parties);
        for (int i = 0; i < parties; i++) {
            pool.submit(() -> {
                try {
                    for (int round = 0; round < rounds; round++) {
                        barrier.await(5, TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allWorkersDone.countDown();
                }
            });
        }

        assertThat(allWorkersDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(barrierActionRuns.get()).isEqualTo(rounds);

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void barrierIsReusableAcrossMultipleRoundsWithSameParties() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        assertThat(barrier.getParties()).isEqualTo(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothRoundsDone = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    barrier.await(5, TimeUnit.SECONDS);
                    bothRoundsDone.countDown();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertThat(bothRoundsDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(barrier.getNumberWaiting()).isZero();

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }
}
