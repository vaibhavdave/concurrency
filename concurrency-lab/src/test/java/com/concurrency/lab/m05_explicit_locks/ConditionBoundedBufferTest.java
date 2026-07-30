package com.concurrency.lab.m05_explicit_locks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ConditionBoundedBufferTest {

    @Test
    void producerBlocksWhenBufferIsFullUntilConsumerMakesRoom() throws InterruptedException {
        ConditionVariableBoundedBufferDemo.BoundedBuffer<Integer> buffer =
                new ConditionVariableBoundedBufferDemo.BoundedBuffer<>(2);
        buffer.put(1);
        buffer.put(2);

        CountDownLatch producerBlocked = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            try {
                producerBlocked.countDown();
                buffer.put(3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();

        assertThat(producerBlocked.await(2, TimeUnit.SECONDS)).isTrue();
        await().during(200, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                .until(() -> buffer.size() == 2);

        assertThat(buffer.take()).isEqualTo(1);
        producer.join(2000);
        assertThat(buffer.size()).isEqualTo(2);
    }

    @Test
    void allProducedItemsAreConsumedInOrderThroughBoundedBuffer() throws InterruptedException {
        ConditionVariableBoundedBufferDemo.BoundedBuffer<Integer> buffer =
                new ConditionVariableBoundedBufferDemo.BoundedBuffer<>(3);
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
        });
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < itemCount; i++) {
                    consumed.add(buffer.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();
        producer.join(5000);
        consumer.join(5000);

        assertThat(consumed).hasSize(itemCount);
        assertThat(consumed).isSorted();
        assertThat(consumed.get(0)).isEqualTo(0);
        assertThat(consumed.get(itemCount - 1)).isEqualTo(itemCount - 1);
    }
}
