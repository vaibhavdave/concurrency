package com.concurrency.lab.m08_executors_and_thread_pools;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolExecutorTuningDemo {

    private static ThreadPoolExecutor buildPool(RejectedExecutionHandler handler) {
        return new ThreadPoolExecutor(
                2,
                2,
                5,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),
                handler);
    }

    private static void demonstrate(String policyName, RejectedExecutionHandler handler) throws InterruptedException {
        System.out.println("== " + policyName + " ==");
        ThreadPoolExecutor pool = buildPool(handler);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger discardedByCaller = new AtomicInteger();

        for (int i = 0; i < 8; i++) {
            int taskId = i;
            try {
                pool.execute(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    completed.incrementAndGet();
                    System.out.println("[" + policyName + "] completed task " + taskId
                            + " on " + Thread.currentThread().getName());
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                discardedByCaller.incrementAndGet();
                System.out.println("[" + policyName + "] task " + taskId + " rejected: " + e.getMessage());
            }
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("[" + policyName + "] completed=" + completed.get()
                + " explicitlyRejected=" + discardedByCaller.get());
    }

    public static void main(String[] args) throws InterruptedException {
        // core=2, max=2, queue capacity=2 -> at most 4 tasks can be admitted; the other 4 of 8 trigger the handler
        demonstrate("AbortPolicy", new ThreadPoolExecutor.AbortPolicy());

        // why CallerRunsPolicy provides backpressure: the submitting thread itself executes the task,
        // which slows down further submission and throttles the producer instead of dropping work
        demonstrate("CallerRunsPolicy", new ThreadPoolExecutor.CallerRunsPolicy());

        // silently drops the new task with no exception and no execution - dangerous unless loss is acceptable
        demonstrate("DiscardPolicy", new ThreadPoolExecutor.DiscardPolicy());

        // evicts the oldest queued task to make room for the new one - favors newer work over older
        demonstrate("DiscardOldestPolicy", new ThreadPoolExecutor.DiscardOldestPolicy());
    }
}
