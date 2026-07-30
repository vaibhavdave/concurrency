package com.concurrency.lab.m02_race_conditions_and_synchronized;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SynchronizedBlockVsMethodDemo {

    static class CoarseGrainedAccount {
        private int checkingBalance;
        private int savingsBalance;

        public synchronized void depositChecking(int amount) {
            sleepQuietly(5);
            checkingBalance += amount;
        }

        public synchronized void depositSavings(int amount) {
            sleepQuietly(5);
            savingsBalance += amount;
        }
    }

    static class FineGrainedAccount {
        private final Object checkingLock = new Object();
        private final Object savingsLock = new Object();
        private int checkingBalance;
        private int savingsBalance;

        public void depositChecking(int amount) {
            synchronized (checkingLock) {
                sleepQuietly(5);
                checkingBalance += amount;
            }
        }

        public void depositSavings(int amount) {
            synchronized (savingsLock) {
                sleepQuietly(5);
                savingsBalance += amount;
            }
        }
    }

    static class DangerouslyExposedLock {
        public final Object lock = new Object();

        public void doInternalWork() {
            synchronized (lock) {
                sleepQuietly(200);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== Coarse-grained: whole-method synchronized(this) serializes UNRELATED fields ==");
        CoarseGrainedAccount coarse = new CoarseGrainedAccount();
        long coarseMs = timeConcurrentDeposits(
                () -> coarse.depositChecking(1),
                () -> coarse.depositSavings(1));
        System.out.println("Coarse-grained elapsed: " + coarseMs + " ms (checking and savings deposits "
                + "contend on the SAME monitor even though they touch different fields)");

        System.out.println("== Fine-grained: private final lock per field allows real parallelism ==");
        FineGrainedAccount fine = new FineGrainedAccount();
        long fineMs = timeConcurrentDeposits(
                () -> fine.depositChecking(1),
                () -> fine.depositSavings(1));
        System.out.println("Fine-grained elapsed: " + fineMs + " ms (should be noticeably less than coarse-grained)");

        System.out.println("== Danger of exposing the lock object publicly ==");
        DangerouslyExposedLock exposed = new DangerouslyExposedLock();
        Thread internalWorker = new Thread(exposed::doInternalWork, "internal-worker");

        Thread hostileOutsider = new Thread(() -> {
            synchronized (exposed.lock) {
                System.out.println("hostile-outsider acquired the SAME public lock and is holding it, "
                        + "starving legitimate internal work");
                sleepQuietly(300);
            }
        }, "hostile-outsider");

        long start = System.nanoTime();
        hostileOutsider.start();
        Thread.sleep(50);
        internalWorker.start();
        internalWorker.join();
        hostileOutsider.join();
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        System.out.println("internal-worker was blocked by unrelated outside code for ~" + elapsed
                + " ms because the lock object was public. A private final lock object would prevent this.");
    }

    private static long timeConcurrentDeposits(Runnable depositChecking, Runnable depositSavings)
            throws InterruptedException {
        int iterations = 30;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Thread checkingThread = new Thread(() -> {
            awaitQuietly(startGate);
            for (int i = 0; i < iterations; i++) {
                depositChecking.run();
            }
            doneLatch.countDown();
        }, "checking-thread");

        Thread savingsThread = new Thread(() -> {
            awaitQuietly(startGate);
            for (int i = 0; i < iterations; i++) {
                depositSavings.run();
            }
            doneLatch.countDown();
        }, "savings-thread");

        long start = System.nanoTime();
        checkingThread.start();
        savingsThread.start();
        startGate.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
