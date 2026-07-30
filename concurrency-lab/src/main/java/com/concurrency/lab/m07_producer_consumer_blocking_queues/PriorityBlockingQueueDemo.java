package com.concurrency.lab.m07_producer_consumer_blocking_queues;

import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;

public class PriorityBlockingQueueDemo {

    record Task(String name, int priority) {
    }

    public static void main(String[] args) throws Exception {
        // higher priority value = more urgent = should be polled first, so the comparator is reversed
        BlockingQueue<Task> queue = new PriorityBlockingQueue<>(11, Comparator.comparingInt(Task::priority).reversed());

        Task[] producedOutOfOrder = {
                new Task("cleanup", 1),
                new Task("send-email", 3),
                new Task("handle-payment", 9),
                new Task("log-metrics", 0),
                new Task("fraud-check", 8),
                new Task("refresh-cache", 2),
        };

        System.out.println("Producing tasks out of priority order:");
        for (Task task : producedOutOfOrder) {
            queue.put(task);
            System.out.println("  produced " + task);
        }

        System.out.println("Consuming: priority order is enforced regardless of production order.");
        CountDownLatch done = new CountDownLatch(1);
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < producedOutOfOrder.length; i++) {
                    Task task = queue.take();
                    System.out.println("  consumed " + task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }, "consumer");
        consumer.start();
        done.await();
        consumer.join();
    }
}
