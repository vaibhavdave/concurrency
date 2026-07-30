package com.concurrency.lab.m05_explicit_locks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionVariableBoundedBufferDemo {

    static class BoundedBuffer<T> {
        private final Deque<T> items = new ArrayDeque<>();
        private final int capacity;
        private final Lock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();

        BoundedBuffer(int capacity) {
            this.capacity = capacity;
        }

        void put(T item) throws InterruptedException {
            lock.lock();
            try {
                while (items.size() == capacity) {
                    notFull.await();
                }
                items.addLast(item);
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        T take() throws InterruptedException {
            lock.lock();
            try {
                while (items.isEmpty()) {
                    notEmpty.await();
                }
                T item = items.removeFirst();
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }

        int size() {
            lock.lock();
            try {
                return items.size();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== ReentrantLock + two Condition bounded buffer ==");
        System.out.println("(contrast with the intrinsic-lock wait/notify version in module m02: here notEmpty and");
        System.out.println(" notFull are separate Condition objects, so a signal only wakes the threads that could");
        System.out.println(" actually make progress, instead of every waiter on the object monitor)");

        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(5);
        int itemCount = 20;

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= itemCount; i++) {
                    buffer.put(i);
                    System.out.println("[producer] put " + i + " (size=" + buffer.size() + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= itemCount; i++) {
                    int item = buffer.take();
                    System.out.println("[consumer] took " + item + " (size=" + buffer.size() + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println("Done. Final buffer size=" + buffer.size());
    }
}
