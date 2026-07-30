package com.concurrency.lab.m13_deadlock_livelock_starvation;

import java.util.concurrent.CountDownLatch;

/**
 * This demo deliberately deadlocks and never terminates — that is the point.
 * Do not call this from a test. Run it manually and inspect it with jstack.
 */
public class DeadlockDemo {

    private static final Object lockA = new Object();
    private static final Object lockB = new Object();

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(2);

        Thread threadOne = new Thread(() -> {
            startLatch.countDown();
            awaitLatch(startLatch);
            synchronized (lockA) {
                System.out.println(Thread.currentThread().getName() + " acquired lockA, waiting for lockB");
                sleepQuietly(50);
                synchronized (lockB) {
                    System.out.println(Thread.currentThread().getName() + " acquired lockB");
                }
            }
        }, "thread-A-then-B");
        threadOne.setDaemon(true);

        Thread threadTwo = new Thread(() -> {
            startLatch.countDown();
            awaitLatch(startLatch);
            synchronized (lockB) {
                System.out.println(Thread.currentThread().getName() + " acquired lockB, waiting for lockA");
                sleepQuietly(50);
                synchronized (lockA) {
                    System.out.println(Thread.currentThread().getName() + " acquired lockA");
                }
            }
        }, "thread-B-then-A");
        threadTwo.setDaemon(true);

        threadOne.start();
        threadTwo.start();

        long pid = ProcessHandle.current().pid();
        System.out.println();
        System.out.println("Both threads started and are acquiring locks in opposite order (A->B vs B->A).");
        System.out.println("This JVM (pid " + pid + ") will now deadlock and hang forever — that is expected.");
        System.out.println();
        System.out.println("To observe the deadlock, run in another terminal:");
        System.out.println("    jstack " + pid);
        System.out.println("Look for the line: \"Found one Java-level deadlock\"");
        System.out.println();
        System.out.println("Press Ctrl-C to stop this process (both threads are daemon threads).");

        threadOne.join();
        threadTwo.join();
        System.out.println("unreachable: threads never finish because they are deadlocked");
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
