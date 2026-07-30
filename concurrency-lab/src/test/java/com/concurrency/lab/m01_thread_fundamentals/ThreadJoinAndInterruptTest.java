package com.concurrency.lab.m01_thread_fundamentals;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ThreadJoinAndInterruptTest {

    @Test
    void joinWithTimeoutReturnsWhileThreadStillRunning() throws InterruptedException {
        CountDownLatch releaseLatch = new CountDownLatch(1);
        Thread longRunning = new Thread(() -> {
            try {
                releaseLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "join-timeout-worker");
        longRunning.start();

        longRunning.join(50);
        assertThat(longRunning.isAlive()).isTrue();

        releaseLatch.countDown();
        longRunning.join();
        assertThat(longRunning.isAlive()).isFalse();
    }

    @Test
    void interruptSetsFlagAndUnblocksSleepingThread() throws InterruptedException {
        AtomicBoolean caughtInterruptedException = new AtomicBoolean(false);
        AtomicBoolean interruptStatusRestored = new AtomicBoolean(false);
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch finishedLatch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            startedLatch.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                caughtInterruptedException.set(true);
                Thread.currentThread().interrupt();
                interruptStatusRestored.set(Thread.currentThread().isInterrupted());
            } finally {
                finishedLatch.countDown();
            }
        }, "interrupt-target");
        worker.start();

        startedLatch.await();
        worker.interrupt();

        await().atMost(Duration.ofSeconds(2))
                .until(() -> finishedLatch.getCount() == 0);
        worker.join();

        assertThat(caughtInterruptedException).isTrue();
        assertThat(interruptStatusRestored).isTrue();
    }
}
