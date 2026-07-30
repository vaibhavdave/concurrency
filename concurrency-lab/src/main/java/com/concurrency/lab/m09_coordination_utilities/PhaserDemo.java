package com.concurrency.lab.m09_coordination_utilities;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

public class PhaserDemo {

    public static void main(String[] args) throws InterruptedException {
        // owner party keeps the phaser alive across phase 0 registration churn
        Phaser phaser = new Phaser(1);

        System.out.println("== Phase 0: 3 workers register ==");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 3; i++) {
            int workerId = i;
            phaser.register();
            pool.submit(() -> runWorker(phaser, workerId));
        }

        phaser.arriveAndAwaitAdvance();
        System.out.println("[main] phase 0 complete, all 3 workers arrived");

        // a 4th worker dynamically joins for phase 1 - CountDownLatch/CyclicBarrier can't change party count mid-run
        System.out.println("== Phase 1: a 4th worker dynamically registers ==");
        phaser.register();
        pool.submit(() -> runWorker(phaser, 3));

        phaser.arriveAndAwaitAdvance();
        System.out.println("[main] phase 1 complete, all 4 workers arrived");

        System.out.println("[main] deregistering owner party, allowing phaser to terminate");
        phaser.arriveAndDeregister();

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("Phaser terminated: " + phaser.isTerminated());
    }

    private static void runWorker(Phaser phaser, int workerId) {
        int phase = phaser.getPhase();
        System.out.println("[worker-" + workerId + "] working in phase " + phase);
        sleepQuietly(30L * (workerId + 1));
        // each worker only participates in a single phase, so it deregisters on arrival rather than
        // staying registered (and stalling) future phases it never shows up for
        System.out.println("[worker-" + workerId + "] arriving and deregistering after phase " + phase);
        phaser.arriveAndDeregister();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
