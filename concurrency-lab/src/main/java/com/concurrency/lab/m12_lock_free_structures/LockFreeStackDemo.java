package com.concurrency.lab.m12_lock_free_structures;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeStackDemo {

    public static void main(String[] args) throws InterruptedException {
        TreiberStack<Integer> stack = new TreiberStack<>();
        int producerThreads = 8;
        int itemsPerProducer = 50_000;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(producerThreads);
        AtomicInteger pushed = new AtomicInteger();

        for (int t = 0; t < producerThreads; t++) {
            int threadId = t;
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < itemsPerProducer; i++) {
                        stack.push(threadId * itemsPerProducer + i);
                        pushed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }, "pusher-" + t).start();
        }

        System.out.println("Starting " + producerThreads + " threads pushing " + itemsPerProducer + " items each...");
        long start = System.nanoTime();
        startLatch.countDown();
        doneLatch.await();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        int expected = producerThreads * itemsPerProducer;
        System.out.println("Pushed " + pushed.get() + " items in " + elapsedMillis + " ms");
        System.out.println("Final stack size = " + stack.size() + " (expected " + expected + ")");

        int popped = 0;
        while (stack.pop() != null) {
            popped++;
        }
        System.out.println("Popped " + popped + " items back off, stack now empty = " + stack.isEmpty());
    }

    public static class TreiberStack<T> {
        private final AtomicReference<Node<T>> top = new AtomicReference<>();
        private final AtomicInteger size = new AtomicInteger();

        public void push(T value) {
            Node<T> newHead = new Node<>(value);
            Node<T> currentHead;
            do {
                currentHead = top.get();
                newHead.next = currentHead;
                // why CAS instead of a lock: if the CAS fails because another thread
                // won the race, we simply retry with the fresh head rather than blocking
            } while (!top.compareAndSet(currentHead, newHead));
            size.incrementAndGet();
        }

        public T pop() {
            Node<T> currentHead;
            Node<T> newHead;
            do {
                currentHead = top.get();
                if (currentHead == null) {
                    return null;
                }
                newHead = currentHead.next;
            } while (!top.compareAndSet(currentHead, newHead));
            size.decrementAndGet();
            return currentHead.value;
        }

        public boolean isEmpty() {
            return top.get() == null;
        }

        public int size() {
            return size.get();
        }

        private static final class Node<T> {
            final T value;
            Node<T> next;

            Node(T value) {
                this.value = value;
            }
        }
    }
}
