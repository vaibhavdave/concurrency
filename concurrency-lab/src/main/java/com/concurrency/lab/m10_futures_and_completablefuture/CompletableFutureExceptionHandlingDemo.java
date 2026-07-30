package com.concurrency.lab.m10_futures_and_completablefuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompletableFutureExceptionHandlingDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        System.out.println("== exceptionally: recover from a failure, only invoked when upstream failed ==");
        CompletableFuture<Integer> exceptionallyRecovered = CompletableFuture
                .<Integer>supplyAsync(() -> {
                    throw new IllegalStateException("boom");
                }, executor)
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    System.out.println("exceptionally caught: " + cause);
                    return -1;
                });
        System.out.println("exceptionally result=" + exceptionallyRecovered.get());

        System.out.println("== handle: runs on BOTH success and failure, receives (result, exception) ==");
        CompletableFuture<String> handledSuccess = CompletableFuture
                .supplyAsync(() -> 21, executor)
                .handle((value, ex) -> ex != null ? "error:" + ex : "value:" + (value * 2));
        System.out.println("handle (success path) result=" + handledSuccess.get());

        CompletableFuture<String> handledFailure = CompletableFuture
                .<Integer>supplyAsync(() -> {
                    throw new RuntimeException("handled-failure");
                }, executor)
                .handle((value, ex) -> ex != null ? "error:" + ex.getCause().getMessage() : "value:" + value);
        System.out.println("handle (failure path) result=" + handledFailure.get());

        System.out.println("== whenComplete: observes (result, exception) but does NOT change the outcome or recover ==");
        CompletableFuture<Integer> observedFailure = CompletableFuture
                .<Integer>supplyAsync(() -> {
                    throw new RuntimeException("whenComplete-failure");
                }, executor)
                .whenComplete((value, ex) -> {
                    if (ex != null) {
                        System.out.println("whenComplete observed failure: " + ex.getCause().getMessage());
                    }
                });
        try {
            observedFailure.get();
        } catch (java.util.concurrent.ExecutionException e) {
            System.out.println("whenComplete did NOT recover - get() still throws: " + e.getCause());
        }

        System.out.println("== exception propagation through a chain: failure short-circuits thenApply/thenCompose ==");
        CompletableFuture<Integer> chainWithFailure = CompletableFuture
                .supplyAsync(() -> 10, executor)
                .<Integer>thenApply(value -> {
                    throw new RuntimeException("mid-chain failure");
                })
                .thenApply(value -> value * 100)
                .exceptionally(ex -> {
                    System.out.println("Downstream thenApply never ran; recovered at exceptionally: " + ex.getCause().getMessage());
                    return -999;
                });
        System.out.println("chainWithFailure result=" + chainWithFailure.get());

        executor.shutdown();
    }
}
