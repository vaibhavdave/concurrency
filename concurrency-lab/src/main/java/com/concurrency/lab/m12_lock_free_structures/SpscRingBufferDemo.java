package com.concurrency.lab.m12_lock_free_structures;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SpscRingBufferDemo {

    public static void main(String[] args) throws InterruptedException {
        SpscRingBuffer<Long> buffer = new SpscRingBuffer<>(1024);
        long itemCount = 5_000_000;

        AtomicLong consumedCount = new AtomicLong();
        AtomicLong inOrderErrors = new AtomicLong();
        AtomicBoolean producerDone = new AtomicBoolean(false);
        CountDownLatch startLatch = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            try {
                startLatch.await();
                for (long i = 0; i < itemCount; i++) {
                    while (!buffer.offer(i)) {
                        Thread.onSpinWait();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                producerDone.set(true);
            }
        }, "spsc-producer");

        Thread consumer = new Thread(() -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long expectedNext = 0;
            while (consumedCount.get() < itemCount) {
                Long value = buffer.poll();
                if (value == null) {
                    Thread.onSpinWait();
                    continue;
                }
                if (value != expectedNext) {
                    inOrderErrors.incrementAndGet();
                }
                expectedNext++;
                consumedCount.incrementAndGet();
            }
        }, "spsc-consumer");

        producer.start();
        consumer.start();
        long start = System.nanoTime();
        startLatch.countDown();
        producer.join();
        consumer.join();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Transferred " + consumedCount.get() + " / " + itemCount + " items in " + elapsedMillis + " ms");
        System.out.println("Out-of-order deliveries = " + inOrderErrors.get() + " (expected 0)");
    }

    public static class SpscRingBuffer<T> {
        private final Object[] elements;
        private final int mask;
        private volatile long tail = 0;
        private volatile long head = 0;

        public SpscRingBuffer(int capacityPowerOfTwo) {
            if (Integer.bitCount(capacityPowerOfTwo) != 1) {
                throw new IllegalArgumentException("capacity must be a power of two");
            }
            this.elements = new Object[capacityPowerOfTwo];
            this.mask = capacityPowerOfTwo - 1;
        }

        public boolean offer(T value) {
            long currentTail = tail;
            if (currentTail - head >= elements.length) {
                return false;
            }
            elements[(int) (currentTail & mask)] = value;
            // publishing the volatile write of tail AFTER the array write establishes
            // happens-before so the consumer never observes a slot before its data
            tail = currentTail + 1;
            return true;
        }

        @SuppressWarnings("unchecked")
        public T poll() {
            long currentHead = head;
            if (currentHead >= tail) {
                return null;
            }
            T value = (T) elements[(int) (currentHead & mask)];
            elements[(int) (currentHead & mask)] = null;
            head = currentHead + 1;
            return value;
        }
    }
}
