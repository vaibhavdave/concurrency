package com.concurrency.lab.m09_coordination_utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExchangerDemo {

    private static final int ROUNDS = 3;

    public static void main(String[] args) throws InterruptedException {
        Exchanger<List<Integer>> exchanger = new Exchanger<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        pool.submit(() -> producer(exchanger));
        pool.submit(() -> consumer(exchanger));

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void producer(Exchanger<List<Integer>> exchanger) {
        List<Integer> buffer = new ArrayList<>();
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                for (int i = 0; i < 3; i++) {
                    buffer.add(round * 10 + i);
                }
                System.out.println("[producer] filled buffer for round " + round + ": " + buffer);
                // blocks until the consumer arrives with its (empty/drained) buffer to swap
                buffer = exchanger.exchange(buffer);
                System.out.println("[producer] received buffer back: " + buffer);
                buffer.clear();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void consumer(Exchanger<List<Integer>> exchanger) {
        List<Integer> buffer = new ArrayList<>();
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                sleepQuietly(50);
                List<Integer> filled = exchanger.exchange(buffer);
                System.out.println("[consumer] drained buffer for round " + round + ": " + filled);
                buffer = new ArrayList<>();
            }
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
