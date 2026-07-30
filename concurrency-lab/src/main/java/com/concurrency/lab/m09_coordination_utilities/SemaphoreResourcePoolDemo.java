package com.concurrency.lab.m09_coordination_utilities;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SemaphoreResourcePoolDemo {

    static class ConnectionPool {
        private final Semaphore permits;

        ConnectionPool(int size, boolean fair) {
            this.permits = new Semaphore(size, fair);
        }

        void useConnection(String clientName) throws InterruptedException {
            System.out.println("[" + clientName + "] waiting for a connection ("
                    + permits.availablePermits() + " available)");
            permits.acquire();
            try {
                System.out.println("[" + clientName + "] acquired connection ("
                        + permits.availablePermits() + " remaining)");
                Thread.sleep(100);
            } finally {
                permits.release();
                System.out.println("[" + clientName + "] released connection");
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int poolSize = 2;
        int clientCount = 5;
        // fair=true grants permits roughly in arrival (FIFO) order instead of allowing barging,
        // trading a little throughput for predictable, starvation-free ordering
        ConnectionPool pool = new ConnectionPool(poolSize, true);
        AtomicInteger completed = new AtomicInteger();

        ExecutorService clients = Executors.newFixedThreadPool(clientCount);
        for (int i = 0; i < clientCount; i++) {
            String clientName = "client-" + i;
            clients.submit(() -> {
                try {
                    pool.useConnection(clientName);
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        clients.shutdown();
        clients.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("All " + completed.get() + " clients completed using at most "
                + poolSize + " concurrent connections.");
    }
}
