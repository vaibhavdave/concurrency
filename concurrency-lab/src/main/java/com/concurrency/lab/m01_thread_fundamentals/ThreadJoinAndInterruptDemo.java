package com.concurrency.lab.m01_thread_fundamentals;

public class ThreadJoinAndInterruptDemo {

    public static void main(String[] args) throws InterruptedException {
        demoJoinWithTimeout();
        demoInterrupt();
    }

    private static void demoJoinWithTimeout() throws InterruptedException {
        System.out.println("== join(timeout) ==");
        Thread slow = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "slow-worker");
        slow.start();

        slow.join(200);
        if (slow.isAlive()) {
            System.out.println("join(200) timed out; slow-worker is still running");
        } else {
            System.out.println("slow-worker finished within 200ms");
        }

        slow.join();
        System.out.println("slow-worker finished for good, state=" + slow.getState());
    }

    private static void demoInterrupt() throws InterruptedException {
        System.out.println("== interrupt() and correct handling ==");
        Thread worker = new Thread(() -> {
            try {
                System.out.println("worker sleeping, waiting to be interrupted...");
                Thread.sleep(10_000);
                System.out.println("worker woke up normally (should not happen in this demo)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("worker caught InterruptedException; restored interrupt status = "
                        + Thread.currentThread().isInterrupted());
            }
        }, "interruptible-worker");

        worker.start();
        Thread.sleep(200);
        System.out.println("main requesting interrupt");
        worker.interrupt();
        worker.join();
        System.out.println("worker terminated, state=" + worker.getState());
    }
}
