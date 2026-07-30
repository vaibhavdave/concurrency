package com.concurrency.lab.m02_race_conditions_and_synchronized;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WaitNotifyBoundedBufferTest {

    @Test
    void putBlocksWhenFullAndUnblocksAfterTake() throws InterruptedException {
        WaitNotifyBoundedBuffer<Integer> buffer = new WaitNotifyBoundedBuffer<>(2);
        buffer.put(1);
        buffer.put(2);

        Thread producer = new Thread(() -> {
            try {
                buffer.put(3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-producer");
        producer.start();

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(1))
                .until(() -> buffer.size() == 2);

        Integer taken = buffer.take();
        assertThat(taken).isEqualTo(1);

        await().atMost(Duration.ofSeconds(2)).until(() -> buffer.size() == 2);
        producer.join();
    }

    @Test
    void producerConsumerTransfersAllItemsInOrder() throws InterruptedException {
        WaitNotifyBoundedBuffer<Integer> buffer = new WaitNotifyBoundedBuffer<>(3);
        int itemCount = 50;
        List<Integer> consumed = new CopyOnWriteArrayList<>();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < itemCount; i++) {
                    buffer.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-producer-2");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < itemCount; i++) {
                    consumed.add(buffer.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-consumer-2");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        assertThat(consumed).hasSize(itemCount);
        for (int i = 0; i < itemCount; i++) {
            assertThat(consumed.get(i)).isEqualTo(i);
        }
    }
}
