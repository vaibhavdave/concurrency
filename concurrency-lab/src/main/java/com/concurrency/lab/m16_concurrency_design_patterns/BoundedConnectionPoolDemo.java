package com.concurrency.lab.m16_concurrency_design_patterns;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BoundedConnectionPoolDemo {

    public static void main(String[] args) throws InterruptedException {
        BoundedConnectionPool pool = new BoundedConnectionPool(2);
        int borrowerCount = 5;
        CountDownLatch allDone = new CountDownLatch(borrowerCount);
        ExecutorService borrowers = Executors.newFixedThreadPool(borrowerCount);

        for (int i = 0; i < borrowerCount; i++) {
            int id = i;
            borrowers.submit(() -> {
                try {
                    System.out.printf("borrower-%d waiting for a connection (available permits=%d)%n",
                            id, pool.availablePermits());
                    BoundedConnectionPool.Connection connection = pool.borrow();
                    System.out.printf("borrower-%d checked out %s%n", id, connection.id());
                    Thread.sleep(150);
                    System.out.printf("borrower-%d returning %s%n", id, connection.id());
                    pool.release(connection);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            });
        }

        allDone.await(10, TimeUnit.SECONDS);
        borrowers.shutdown();
        System.out.println("Final available permits: " + pool.availablePermits());
    }
}
