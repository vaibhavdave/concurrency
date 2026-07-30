package com.concurrency.lab.m09_coordination_utilities;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CyclicBarrierDemo {

    public static void main(String[] args) throws InterruptedException {
        int parties = 3;
        int rounds = 3;
        AtomicInteger currentRound = new AtomicInteger(1);
        List<Integer> roundResults = new CopyOnWriteArrayList<>();

        // the barrier action runs once per round, on one of the arriving threads, only after ALL parties arrive
        CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
            int total = roundResults.stream().mapToInt(Integer::intValue).sum();
            System.out.println("[barrier-action] round " + currentRound.get() + " complete, aggregate=" + total);
            roundResults.clear();
            currentRound.incrementAndGet();
        });

        ExecutorService pool = Executors.newFixedThreadPool(parties);
        for (int i = 0; i < parties; i++) {
            int workerId = i;
            pool.submit(() -> {
                try {
                    for (int round = 1; round <= rounds; round++) {
                        int contribution = (workerId + 1) * round;
                        sleepQuietly(20L * (workerId + 1));
                        roundResults.add(contribution);
                        System.out.println("[worker-" + workerId + "] round " + round
                                + " contributed " + contribution + ", waiting at barrier");
                        barrier.await();
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("All rounds completed.");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
