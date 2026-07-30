package com.concurrency.lab.m10_futures_and_completablefuture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CompletableFutureTimeoutAndCombiningDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        System.out.println("== orTimeout: fails the future with TimeoutException if not complete in time ==");
        CompletableFuture<String> orTimeoutFuture = CompletableFuture
                .supplyAsync(() -> sleepThenReturn(500, "too-slow"), executor)
                .orTimeout(100, TimeUnit.MILLISECONDS);
        try {
            orTimeoutFuture.get();
        } catch (java.util.concurrent.ExecutionException e) {
            // why we must handle both TimeoutException and CompletionException: get() wraps orTimeout's
            // TimeoutException in ExecutionException, while join()/other chained stages wrap it in CompletionException
            System.out.println("orTimeout caused get() to throw ExecutionException wrapping: " + e.getCause().getClass().getSimpleName());
        }

        System.out.println("== completeOnTimeout: supplies a fallback value instead of failing ==");
        CompletableFuture<String> completeOnTimeoutFuture = CompletableFuture
                .supplyAsync(() -> sleepThenReturn(500, "too-slow"), executor)
                .completeOnTimeout("fallback-value", 100, TimeUnit.MILLISECONDS);
        System.out.println("completeOnTimeout result=" + completeOnTimeoutFuture.get());

        System.out.println("== allOf: wait for several independent async calls, then aggregate all results ==");
        CompletableFuture<String> serviceA = CompletableFuture.supplyAsync(() -> sleepThenReturn(50, "A-result"), executor);
        CompletableFuture<String> serviceB = CompletableFuture.supplyAsync(() -> sleepThenReturn(80, "B-result"), executor);
        CompletableFuture<String> serviceC = CompletableFuture.supplyAsync(() -> sleepThenReturn(30, "C-result"), executor);

        CompletableFuture<List<String>> allResults = CompletableFuture
                .allOf(serviceA, serviceB, serviceC)
                .thenApply(unused -> List.of(serviceA.join(), serviceB.join(), serviceC.join()));
        System.out.println("allOf aggregated=" + allResults.get());

        System.out.println("== anyOf: proceed as soon as the FIRST of several futures completes ==");
        CompletableFuture<Object> fastest = CompletableFuture.anyOf(
                CompletableFuture.supplyAsync(() -> sleepThenReturn(200, "slow-service"), executor),
                CompletableFuture.supplyAsync(() -> sleepThenReturn(20, "fast-service"), executor),
                CompletableFuture.supplyAsync(() -> sleepThenReturn(100, "medium-service"), executor));
        System.out.println("anyOf first result=" + fastest.get());

        System.out.println("== allOf with one failing future: the combined future fails, but individual results are still retrievable ==");
        CompletableFuture<String> healthyService = CompletableFuture.supplyAsync(() -> sleepThenReturn(20, "healthy"), executor);
        CompletableFuture<String> failingService = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("downstream-unavailable");
        }, executor);
        CompletableFuture<Void> combined = CompletableFuture.allOf(healthyService, failingService);
        try {
            combined.get();
        } catch (java.util.concurrent.ExecutionException e) {
            System.out.println("allOf failed fast because failingService failed: " + e.getCause().getMessage());
            System.out.println("healthyService still completed normally: " + healthyService.join());
        }

        executor.shutdown();
    }

    private static String sleepThenReturn(long millis, String value) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
        return value;
    }
}
