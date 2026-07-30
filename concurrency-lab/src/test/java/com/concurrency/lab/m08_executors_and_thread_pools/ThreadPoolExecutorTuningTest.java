package com.concurrency.lab.m08_executors_and_thread_pools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class ThreadPoolExecutorTuningTest {

    @Test
    void abortPolicyRejectsWhenPoolAndQueueAreFull() throws InterruptedException {
        CountDownLatch blockWorkers = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 5, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        pool.execute(() -> awaitQuietly(blockWorkers));
        pool.execute(() -> awaitQuietly(blockWorkers));

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> pool.getQueue().size() == 1);

        assertThat(pool.getActiveCount()).isEqualTo(1);
        assertThatSubmittingRejects(pool);

        blockWorkers.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private void assertThatSubmittingRejects(ThreadPoolExecutor pool) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pool.execute(() -> { }))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void callerRunsPolicyExecutesRejectedTaskOnCallingThread() throws InterruptedException {
        CountDownLatch blockWorkers = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 5, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy());

        pool.execute(() -> awaitQuietly(blockWorkers));
        pool.execute(() -> awaitQuietly(blockWorkers));

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> pool.getQueue().size() == 1);

        AtomicInteger callerRunThread = new AtomicInteger();
        String testThreadId = String.valueOf(Thread.currentThread().getId());
        pool.execute(() -> {
            if (String.valueOf(Thread.currentThread().getId()).equals(testThreadId)) {
                callerRunThread.incrementAndGet();
            }
        });

        assertThat(callerRunThread.get()).isEqualTo(1);

        blockWorkers.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void discardPolicySilentlyDropsTaskWithoutExecutingOrThrowing() throws InterruptedException {
        CountDownLatch blockWorkers = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 5, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.DiscardPolicy());

        pool.execute(() -> awaitQuietly(blockWorkers));
        pool.execute(() -> awaitQuietly(blockWorkers));

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> pool.getQueue().size() == 1);

        AtomicInteger discardedTaskRan = new AtomicInteger();
        pool.execute(discardedTaskRan::incrementAndGet);

        blockWorkers.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(discardedTaskRan.get()).isZero();
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
