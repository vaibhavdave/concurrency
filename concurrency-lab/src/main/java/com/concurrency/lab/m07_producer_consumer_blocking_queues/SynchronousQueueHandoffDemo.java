package com.concurrency.lab.m07_producer_consumer_blocking_queues;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class SynchronousQueueHandoffDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("A SynchronousQueue has zero capacity: put() blocks until another thread is already");
        System.out.println("waiting in take() (and vice versa) -- every element is a direct thread-to-thread handoff,");
        System.out.println("never buffered.");

        BlockingQueue<String> handoff = new SynchronousQueue<>();
        ExecutorService consumers = Executors.newCachedThreadPool();
        int taskCount = 5;

        for (int i = 0; i < taskCount; i++) {
            int consumerId = i;
            consumers.submit(() -> {
                try {
                    String task = handoff.take();
                    System.out.println("[consumer-" + consumerId + "] received '" + task + "' directly from producer");
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= taskCount; i++) {
                    String task = "task-" + i;
                    long begin = System.nanoTime();
                    handoff.put(task);
                    long blockedMillis = (System.nanoTime() - begin) / 1_000_000;
                    System.out.println("[producer] handed off '" + task + "' (blocked " + blockedMillis
                            + "ms waiting for a consumer thread)");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        producer.start();
        producer.join();
        consumers.shutdown();
        consumers.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("All tasks handed off directly; the queue itself never held more than 0 elements.");
    }
}
