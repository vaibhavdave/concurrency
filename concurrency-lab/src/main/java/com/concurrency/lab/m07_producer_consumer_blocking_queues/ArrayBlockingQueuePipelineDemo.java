package com.concurrency.lab.m07_producer_consumer_blocking_queues;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ArrayBlockingQueuePipelineDemo {

    public static void main(String[] args) throws Exception {
        int capacity = 3;
        int itemCount = 10;
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(capacity);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= itemCount; i++) {
                    long begin = System.nanoTime();
                    queue.put(i);
                    long waitedMillis = (System.nanoTime() - begin) / 1_000_000;
                    System.out.println("[producer] put " + i + " (blocked " + waitedMillis + "ms, queue size="
                            + queue.size() + "/" + capacity + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= itemCount; i++) {
                    Thread.sleep(50);
                    int item = queue.take();
                    System.out.println("[consumer] took " + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        System.out.println("Bounded queue capacity=" + capacity + "; consumer is deliberately slower than the");
        System.out.println("producer, so put() will block once the queue fills up (backpressure).");
        consumer.start();
        producer.start();
        producer.join();
        consumer.join();
        System.out.println("Pipeline complete.");
    }
}
