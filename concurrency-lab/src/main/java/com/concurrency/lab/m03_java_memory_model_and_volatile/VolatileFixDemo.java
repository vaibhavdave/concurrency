package com.concurrency.lab.m03_java_memory_model_and_volatile;

public class VolatileFixDemo {

    private static volatile boolean stopRequested = false;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long iterations = 0;
            while (!stopRequested) {
                iterations++;
            }
            System.out.println("worker observed stopRequested and exited after " + iterations + " iterations");
        }, "volatile-worker");
        worker.start();

        Thread.sleep(200);
        long before = System.nanoTime();
        System.out.println("main setting stopRequested = true (volatile write)");
        stopRequested = true;

        worker.join(3000);
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;
        System.out.println("worker terminated within " + elapsedMs
                + " ms of the volatile write - the write happens-before the worker's next read, "
                + "so visibility is guaranteed, not just likely.");
    }
}
