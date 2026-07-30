package com.concurrency.lab.m05_explicit_locks;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

public class StampedLockOptimisticReadDemo {

    static class Point {
        private double x;
        private double y;
        private final StampedLock lock = new StampedLock();

        void move(double dx, double dy) {
            long stamp = lock.writeLock();
            try {
                x += dx;
                y += dy;
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        double distanceFromOrigin() {
            long stamp = lock.tryOptimisticRead();
            double currentX = x;
            double currentY = y;

            // must re-validate: an optimistic read takes no lock, so a concurrent writer could have
            // mutated x/y between the two reads above; validate() detects that and we fall back safely
            if (!lock.validate(stamp)) {
                stamp = lock.readLock();
                try {
                    currentX = x;
                    currentY = y;
                } finally {
                    lock.unlockRead(stamp);
                }
            }
            return Math.sqrt(currentX * currentX + currentY * currentY);
        }

        boolean readOnceRequiredFallback() {
            long stamp = lock.tryOptimisticRead();
            double currentX = x;
            double currentY = y;
            if (lock.validate(stamp)) {
                return false;
            }
            stamp = lock.readLock();
            try {
                currentX = x;
                currentY = y;
            } finally {
                lock.unlockRead(stamp);
            }
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. Optimistic read succeeds when no writer interferes ==");
        Point point = new Point();
        point.move(3, 4);
        System.out.println("distance=" + point.distanceFromOrigin() + " (expected 5.0)");

        System.out.println("== 2. Optimistic read under concurrent writes occasionally falls back to readLock() ==");
        AtomicInteger optimisticFallbacks = new AtomicInteger();
        AtomicBoolean stopWriter = new AtomicBoolean(false);
        int readerThreads = 4;
        int readsPerThread = 5_000;
        ExecutorService readerPool = Executors.newFixedThreadPool(readerThreads);
        CountDownLatch readersDone = new CountDownLatch(readerThreads);

        Thread writer = new Thread(() -> {
            while (!stopWriter.get()) {
                point.move(1, -1);
            }
        }, "writer");
        writer.start();

        for (int t = 0; t < readerThreads; t++) {
            readerPool.submit(() -> {
                for (int i = 0; i < readsPerThread; i++) {
                    if (point.readOnceRequiredFallback()) {
                        optimisticFallbacks.incrementAndGet();
                    }
                }
                readersDone.countDown();
            });
        }
        readersDone.await();
        stopWriter.set(true);
        writer.join();
        readerPool.shutdown();

        int totalReads = readerThreads * readsPerThread;
        System.out.println("Total optimistic reads=" + totalReads + ", fell back to lock="
                + optimisticFallbacks.get() + " ("
                + String.format("%.2f", 100.0 * optimisticFallbacks.get() / totalReads) + "%)");
        System.out.println("Most optimistic reads succeed without ever blocking on a lock -> StampedLock beats");
        System.out.println("ReadWriteLock on read-heavy/short-critical-section workloads because readers don't");
        System.out.println("need to write to shared lock-state (no cache-line contention among readers); they only");
        System.out.println("pay the readLock() acquisition cost on the rare validate() failure.");
    }
}
