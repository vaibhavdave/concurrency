package com.concurrency.lab.m02_race_conditions_and_synchronized;

public class WaitNotifyBoundedBufferDemo {

    public static void main(String[] args) throws InterruptedException {
        WaitNotifyBoundedBuffer<Integer> buffer = new WaitNotifyBoundedBuffer<>(5);
        int itemsToProduce = 20;

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= itemsToProduce; i++) {
                    buffer.put(i);
                    System.out.println("produced " + i + " (buffer size=" + buffer.size() + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= itemsToProduce; i++) {
                    int item = buffer.take();
                    System.out.println("                         consumed " + item);
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println("Done. Final buffer size = " + buffer.size());
    }
}
