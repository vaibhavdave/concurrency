package com.concurrency.lab.m01_thread_fundamentals;

public class DaemonThreadDemo {

    public static void main(String[] args) throws InterruptedException {
        Thread daemon = new Thread(() -> {
            int i = 0;
            while (true) {
                i++;
                if (i % 500_000_000 == 0) {
                    System.out.println("daemon thread still alive (tick " + i + ")");
                }
            }
        }, "daemon-worker");
        daemon.setDaemon(true);
        daemon.start();

        Thread nonDaemon = new Thread(() -> {
            System.out.println("non-daemon thread doing a short task");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("non-daemon thread finished");
        }, "non-daemon-worker");
        nonDaemon.setDaemon(false);
        nonDaemon.start();

        System.out.println("main thread reaching end of main() in 300ms...");
        Thread.sleep(300);
        System.out.println("main thread returning from main(); JVM will exit once all "
                + "non-daemon threads finish. The daemon thread is killed abruptly, "
                + "the non-daemon thread is allowed to run to completion.");
    }
}
