package com.concurrency.lab.m08_executors_and_thread_pools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class GracefulShutdownTest {

    @Test
    void shutdownLetsSubmittedTaskCompleteAndRejectsNewSubmissions() throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch taskCompleted = new CountDownLatch(1);

        pool.submit(taskCompleted::countDown);
        pool.shutdown();

        assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pool.isShutdown()).isTrue();

        assertThatSubmittingAfterShutdownIsRejected(pool);
    }

    private void assertThatSubmittingAfterShutdownIsRejected(ExecutorService pool) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pool.submit(() -> { }))
                .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
    }

    @Test
    void shutdownNowInterruptsRunningTaskAndReturnsUnstartedOnes() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        CountDownLatch taskStarted = new CountDownLatch(1);
        AtomicBoolean interruptedInsideTask = new AtomicBoolean(false);

        pool.submit(() -> {
            taskStarted.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interruptedInsideTask.set(true);
                Thread.currentThread().interrupt();
            }
        });
        pool.submit(() -> { });

        assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

        List<Runnable> neverStarted = pool.shutdownNow();

        assertThat(neverStarted).hasSize(1);
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(interruptedInsideTask.get()).isTrue();
    }

    @Test
    void gracefulSequenceEscalatesToShutdownNowWhenTasksDoNotFinishInTime() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        CountDownLatch taskStarted = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        pool.submit(() -> {
            taskStarted.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

        pool.shutdown();
        boolean finishedGracefully = pool.awaitTermination(200, TimeUnit.MILLISECONDS);
        assertThat(finishedGracefully).isFalse();

        pool.shutdownNow();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted.get()).isTrue();
    }
}
