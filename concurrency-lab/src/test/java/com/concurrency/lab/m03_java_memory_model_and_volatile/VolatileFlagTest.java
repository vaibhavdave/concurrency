package com.concurrency.lab.m03_java_memory_model_and_volatile;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class VolatileFlagTest {

    private static volatile boolean stopRequested = false;

    @Test
    void volatileFlagBecomesVisibleToWorkerPromptly() throws InterruptedException {
        stopRequested = false;
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch stoppedLatch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            startedLatch.countDown();
            while (!stopRequested) {
                Thread.onSpinWait();
            }
            stoppedLatch.countDown();
        }, "volatile-flag-worker");
        worker.start();

        startedLatch.await();
        stopRequested = true;

        await().atMost(Duration.ofSeconds(2))
                .until(() -> stoppedLatch.getCount() == 0);

        worker.join();
        assertThat(stoppedLatch.getCount()).isZero();
    }
}
