package com.concurrency.lab.m01_thread_fundamentals;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ThreadLifecycleTest {

    @Test
    void newThreadHasNewState() {
        Thread t = new Thread(() -> { });
        assertThat(t.getState()).isEqualTo(Thread.State.NEW);
    }

    @Test
    void threadWaitingOnLatchReportsWaitingState() throws InterruptedException {
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);

        Thread waiter = new Thread(() -> {
            startedLatch.countDown();
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-waiter");
        waiter.start();

        startedLatch.await();
        await().atMost(Duration.ofSeconds(2))
                .until(() -> waiter.getState() == Thread.State.WAITING);

        blockLatch.countDown();
        waiter.join();
        assertThat(waiter.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    @Test
    void threadTerminatesAfterJoin() throws InterruptedException {
        CountDownLatch doneLatch = new CountDownLatch(1);
        Thread t = new Thread(doneLatch::countDown, "test-terminator");
        t.start();

        await().atMost(Duration.ofSeconds(2))
                .until(() -> doneLatch.getCount() == 0);
        t.join();

        assertThat(t.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    @Test
    void secondThreadBlocksOnHeldMonitor() throws InterruptedException {
        Object lock = new Object();
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch blockedStarted = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (lock) {
                lockHeld.countDown();
                try {
                    releaseLock.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "test-holder");

        Thread blocked = new Thread(() -> {
            blockedStarted.countDown();
            synchronized (lock) {
                // acquired only after holder releases
            }
        }, "test-blocked");

        holder.start();
        lockHeld.await();
        blocked.start();
        blockedStarted.await();

        await().atMost(Duration.ofSeconds(2))
                .until(() -> blocked.getState() == Thread.State.BLOCKED);

        releaseLock.countDown();
        holder.join();
        blocked.join();
    }
}
