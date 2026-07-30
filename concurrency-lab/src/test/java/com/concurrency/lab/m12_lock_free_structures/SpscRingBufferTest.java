package com.concurrency.lab.m12_lock_free_structures;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SpscRingBufferTest {

    @Test
    void producedSequenceEqualsConsumedSequenceInOrder() throws InterruptedException {
        SpscRingBufferDemo.SpscRingBuffer<Integer> buffer = new SpscRingBufferDemo.SpscRingBuffer<>(64);
        int itemCount = 100_000;
        List<Integer> consumed = new ArrayList<>(itemCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch consumerDone = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < itemCount; i++) {
                    while (!buffer.offer(i)) {
                        Thread.onSpinWait();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-spsc-producer");

        Thread consumer = new Thread(() -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            while (consumed.size() < itemCount) {
                Integer value = buffer.poll();
                if (value == null) {
                    Thread.onSpinWait();
                    continue;
                }
                consumed.add(value);
            }
            consumerDone.countDown();
        }, "test-spsc-consumer");

        producer.start();
        consumer.start();
        startLatch.countDown();

        await().atMost(Duration.ofSeconds(30)).until(() -> consumerDone.getCount() == 0);
        producer.join();
        consumer.join();

        assertThat(consumed).hasSize(itemCount);
        for (int i = 0; i < itemCount; i++) {
            assertThat(consumed.get(i)).as("item at position %d", i).isEqualTo(i);
        }
    }

    @Test
    void pollOnEmptyBufferReturnsNull() {
        SpscRingBufferDemo.SpscRingBuffer<String> buffer = new SpscRingBufferDemo.SpscRingBuffer<>(8);
        assertThat(buffer.poll()).isNull();
    }

    @Test
    void offerFailsWhenBufferIsFull() {
        SpscRingBufferDemo.SpscRingBuffer<Integer> buffer = new SpscRingBufferDemo.SpscRingBuffer<>(4);
        assertThat(buffer.offer(1)).isTrue();
        assertThat(buffer.offer(2)).isTrue();
        assertThat(buffer.offer(3)).isTrue();
        assertThat(buffer.offer(4)).isTrue();
        assertThat(buffer.offer(5)).isFalse();
    }
}
