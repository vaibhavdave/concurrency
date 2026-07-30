package com.concurrency.lab.m07_producer_consumer_blocking_queues;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingQueuePipelineTest {

    @Test
    void arrayBlockingQueueDeliversItemsInFifoOrderWithBackpressure() throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        int itemCount = 20;

        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < itemCount; i++) {
                    queue.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();

        List<Integer> received = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            Integer item = queue.poll(5, TimeUnit.SECONDS);
            assertThat(item).as("item %d should arrive within timeout", i).isNotNull();
            received.add(item);
        }
        producer.join(5000);

        assertThat(received).containsExactlyElementsOf(java.util.stream.IntStream.range(0, itemCount).boxed().toList());
    }

    @Test
    void synchronousQueueHandsOffDirectlyBetweenProducerAndConsumer() throws InterruptedException {
        SynchronousQueue<String> handoff = new SynchronousQueue<>();
        CountDownLatch consumerReady = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                consumerReady.countDown();
                String received = handoff.take();
                assertThat(received).isEqualTo("payload");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        consumer.start();

        assertThat(consumerReady.await(5, TimeUnit.SECONDS)).isTrue();
        boolean offered = handoff.offer("payload", 5, TimeUnit.SECONDS);
        assertThat(offered).isTrue();

        consumer.join(5000);
        assertThat(consumer.isAlive()).isFalse();
    }

    @Test
    void priorityBlockingQueueDrainsInPriorityOrderRegardlessOfInsertionOrder() throws InterruptedException {
        record Task(String name, int priority) {
        }
        PriorityBlockingQueue<Task> queue =
                new PriorityBlockingQueue<>(11, Comparator.comparingInt(Task::priority).reversed());

        queue.put(new Task("low", 1));
        queue.put(new Task("high", 9));
        queue.put(new Task("medium", 5));

        assertThat(queue.poll(2, TimeUnit.SECONDS).name()).isEqualTo("high");
        assertThat(queue.poll(2, TimeUnit.SECONDS).name()).isEqualTo("medium");
        assertThat(queue.poll(2, TimeUnit.SECONDS).name()).isEqualTo("low");
    }

    @Test
    void multiStagePipelineProducesExpectedSumsInOrder() throws InterruptedException {
        List<int[]> inputs = List.of(new int[]{1, 2}, new int[]{10, 20}, new int[]{5, 5});
        BlockingQueue<int[]> parsedQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> sumsQueue = new LinkedBlockingQueue<>();

        Thread transformStage = new Thread(() -> {
            try {
                for (int i = 0; i < inputs.size(); i++) {
                    int[] pair = parsedQueue.take();
                    sumsQueue.put(pair[0] + pair[1]);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        transformStage.start();

        for (int[] pair : inputs) {
            parsedQueue.put(pair);
        }

        List<Integer> sums = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            Integer sum = sumsQueue.poll(5, TimeUnit.SECONDS);
            assertThat(sum).isNotNull();
            sums.add(sum);
        }
        transformStage.join(5000);

        assertThat(sums).containsExactly(3, 30, 10);
    }
}
