package com.concurrency.lab.m14_virtual_threads_structured_concurrency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class VirtualThreadsThroughputTest {

    private static final int TASK_COUNT = 500;

    @Test
    void allTasksCompleteOnVirtualThreadPerTaskExecutor() throws InterruptedException {
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch allDone = new CountDownLatch(TASK_COUNT);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASK_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(5);
                        completed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        allDone.countDown();
                    }
                });
            }
            allDone.await();
        }

        assertThat(completed.get()).isEqualTo(TASK_COUNT);
    }

    @Test
    void taskRunningOnVirtualThreadReportsIsVirtualTrue() {
        AtomicBoolean isVirtual = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                isVirtual.set(Thread.currentThread().isVirtual());
                done.countDown();
            });
        }

        await().atMost(Duration.ofSeconds(2)).until(() -> done.getCount() == 0);
        assertThat(isVirtual).isTrue();
    }
}
