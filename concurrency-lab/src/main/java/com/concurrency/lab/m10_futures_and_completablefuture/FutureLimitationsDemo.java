package com.concurrency.lab.m10_futures_and_completablefuture;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureLimitationsDemo {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        System.out.println("== Future.get() blocks the calling thread until the result is ready ==");
        Callable<Integer> slowComputation = () -> {
            Thread.sleep(200);
            return 42;
        };
        Future<Integer> future = pool.submit(slowComputation);
        long start = System.currentTimeMillis();
        try {
            Integer result = future.get();
            System.out.println("Result=" + result + " after blocking " + (System.currentTimeMillis() - start) + "ms");
        } catch (ExecutionException e) {
            System.out.println("Task threw: " + e.getCause());
        }

        System.out.println("== Future.get(timeout) throws TimeoutException instead of blocking forever ==");
        Future<Integer> slowFuture = pool.submit(() -> {
            Thread.sleep(5000);
            return 99;
        });
        try {
            slowFuture.get(100, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            System.out.println("Timed out waiting for slow task, cancelling it");
            slowFuture.cancel(true);
        } catch (ExecutionException e) {
            System.out.println("Task threw: " + e.getCause());
        }

        System.out.println("== cancel(true) interrupts a running task; cancel(false) only prevents unstarted ones ==");
        try {
            System.out.println("slowFuture.isCancelled()=" + slowFuture.isCancelled());
            slowFuture.get();
        } catch (CancellationException e) {
            System.out.println("get() after cancel() throws CancellationException as expected");
        } catch (ExecutionException e) {
            System.out.println("Task threw: " + e.getCause());
        }

        System.out.println("== Future has no composition API: no thenApply/thenCombine, no callback on completion ==");
        System.out.println("You must call get() and block to react to a Future's result - see "
                + "CompletableFutureChainingDemo for the fix (this module's next demo).");

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }
}
