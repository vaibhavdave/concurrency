package com.concurrency.lab.m12_lock_free_structures;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LockFreeStackTest {

    @Test
    void concurrentPushAndPopProduceCorrectFinalSizeWithNoLostOrDuplicatedElements() throws InterruptedException {
        LockFreeStackDemo.TreiberStack<Integer> stack = new LockFreeStackDemo.TreiberStack<>();
        int producerThreads = 8;
        int itemsPerProducer = 5_000;
        int totalItems = producerThreads * itemsPerProducer;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(producerThreads);

        for (int t = 0; t < producerThreads; t++) {
            int base = t * itemsPerProducer;
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < itemsPerProducer; i++) {
                        stack.push(base + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertThat(stack.size()).isEqualTo(totalItems);

        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        AtomicInteger poppedCount = new AtomicInteger();
        Integer value;
        while ((value = stack.pop()) != null) {
            boolean firstTimeSeen = seen.add(value);
            assertThat(firstTimeSeen).as("value %d must not be popped twice", value).isTrue();
            poppedCount.incrementAndGet();
        }

        assertThat(poppedCount.get()).isEqualTo(totalItems);
        assertThat(stack.isEmpty()).isTrue();
    }

    @Test
    void popOnEmptyStackReturnsNull() {
        LockFreeStackDemo.TreiberStack<String> stack = new LockFreeStackDemo.TreiberStack<>();
        assertThat(stack.pop()).isNull();
        assertThat(stack.isEmpty()).isTrue();
    }
}
