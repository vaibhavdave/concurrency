package com.concurrency.lab.m01_thread_fundamentals;

import java.util.concurrent.CountDownLatch;

public class ThreadLifecycleDemo {

    public static void main(String[] args) throws InterruptedException {
        demoNewAndRunnable();
        demoBlocked();
        demoWaiting();
        demoTimedWaiting();
        demoTerminated();
    }

    private static void demoNewAndRunnable() throws InterruptedException {
        System.out.println("== NEW and RUNNABLE ==");
        Thread t = new Thread(() -> {
            long sum = 0;
            for (long i = 0; i < 200_000_000L; i++) {
                sum += i;
            }
            System.out.println("busy-loop result (ignored): " + sum);
        }, "runnable-demo");

        System.out.println("Before start(): " + t.getState());
        t.start();
        Thread.sleep(5);
        System.out.println("Shortly after start(): " + t.getState());
        t.join();
        System.out.println("After join(): " + t.getState());
    }

    private static void demoBlocked() throws InterruptedException {
        System.out.println("== BLOCKED (contending on a monitor) ==");
        Object lock = new Object();
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (lock) {
                lockHeld.countDown();
                try {
                    releaseLock.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "lock-holder");

        Thread blocked = new Thread(() -> {
            synchronized (lock) {
                System.out.println("blocked-thread finally acquired the lock");
            }
        }, "blocked-thread");

        holder.start();
        lockHeld.await();

        blocked.start();
        Thread.sleep(100);
        System.out.println("blocked-thread state while waiting on monitor: " + blocked.getState());

        releaseLock.countDown();
        holder.join();
        blocked.join();
    }

    private static void demoWaiting() throws InterruptedException {
        System.out.println("== WAITING (CountDownLatch.await() with no timeout) ==");
        CountDownLatch latch = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "waiter-thread");

        waiter.start();
        Thread.sleep(100);
        System.out.println("waiter-thread state: " + waiter.getState());

        latch.countDown();
        waiter.join();
    }

    private static void demoTimedWaiting() throws InterruptedException {
        System.out.println("== TIMED_WAITING (Thread.sleep) ==");
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sleeper-thread");

        sleeper.start();
        Thread.sleep(100);
        System.out.println("sleeper-thread state: " + sleeper.getState());
        sleeper.join();
    }

    private static void demoTerminated() throws InterruptedException {
        System.out.println("== TERMINATED ==");
        Thread t = new Thread(() -> System.out.println("finishing quickly"), "terminated-demo");
        t.start();
        t.join();
        System.out.println("terminated-demo state after join(): " + t.getState());
    }
}
