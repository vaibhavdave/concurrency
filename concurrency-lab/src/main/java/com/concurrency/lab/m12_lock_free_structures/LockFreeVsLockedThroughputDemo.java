package com.concurrency.lab.m12_lock_free_structures;

import java.util.concurrent.CountDownLatch;

public class LockFreeVsLockedThroughputDemo {

    public static void main(String[] args) throws InterruptedException {
        int[] threadCounts = {1, 2, 4, 8, 16};
        int opsPerThread = 200_000;

        System.out.printf("%-10s %-20s %-20s%n", "threads", "locked (ms)", "lock-free (ms)");
        for (int threads : threadCounts) {
            long lockedMillis = runLockedBenchmark(threads, opsPerThread);
            long lockFreeMillis = runLockFreeBenchmark(threads, opsPerThread);
            System.out.printf("%-10d %-20d %-20d%n", threads, lockedMillis, lockFreeMillis);
        }

        System.out.println();
        System.out.println("Lock-free tends to win when critical sections are short and contention is high:");
        System.out.println("  the CAS retry loop is cheap compared to blocking/unblocking a synchronized monitor.");
        System.out.println("Locks can win when the critical section is long or retries are expensive:");
        System.out.println("  under heavy contention a CAS loop can retry many times (wasted work, cache-line");
        System.out.println("  bouncing) whereas a blocked thread simply parks and stops burning CPU.");
    }

    private static long runLockedBenchmark(int threadCount, int opsPerThread) throws InterruptedException {
        LockedStack<Integer> stack = new LockedStack<>();
        return runPushPopBenchmark(threadCount, opsPerThread, stack::push, stack::pop);
    }

    private static long runLockFreeBenchmark(int threadCount, int opsPerThread) throws InterruptedException {
        LockFreeStackDemo.TreiberStack<Integer> stack = new LockFreeStackDemo.TreiberStack<>();
        return runPushPopBenchmark(threadCount, opsPerThread, stack::push, stack::pop);
    }

    private static long runPushPopBenchmark(int threadCount, int opsPerThread,
                                             java.util.function.Consumer<Integer> push,
                                             java.util.function.Supplier<Integer> pop) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Thread[] workers = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            workers[t] = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        push.accept(i);
                        pop.get();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            workers[t].start();
        }

        long start = System.nanoTime();
        startLatch.countDown();
        doneLatch.await();
        return (System.nanoTime() - start) / 1_000_000;
    }

    static class LockedStack<T> {
        private Node<T> top;

        public synchronized void push(T value) {
            Node<T> node = new Node<>(value);
            node.next = top;
            top = node;
        }

        public synchronized T pop() {
            if (top == null) {
                return null;
            }
            T value = top.value;
            top = top.next;
            return value;
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
