package com.concurrency.lab.m03_java_memory_model_and_volatile;

public class VisibilityProblemDemo {

    private static boolean stopRequested = false;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long iterations = 0;
            while (!stopRequested) {
                iterations++;
            }
            System.out.println("worker observed stopRequested and exited after " + iterations + " iterations");
        }, "visibility-worker");
        // daemon: guarantees the JVM can still exit even if the visibility bug
        // manifests as a genuine infinite loop on this machine/JIT
        worker.setDaemon(true);
        worker.start();

        Thread.sleep(200);
        System.out.println("main setting stopRequested = true (non-volatile write)");
        stopRequested = true;

        worker.join(3000);
        if (worker.isAlive()) {
            System.out.println("SAFETY TIMEOUT: worker never observed the plain (non-volatile) write "
                    + "after 3s. This is the visibility problem: without a happens-before edge, the JIT "
                    + "may cache stopRequested in a register/CPU cache and the worker spins forever. "
                    + "Behavior is JIT/JVM/hardware dependent - it may also happen to work on your machine.");
            worker.interrupt();
        } else {
            System.out.println("worker exited normally this run (visibility bugs are timing/JIT dependent - "
                    + "run again, or see VolatileFixDemo for the guaranteed-correct version)");
        }
    }
}
