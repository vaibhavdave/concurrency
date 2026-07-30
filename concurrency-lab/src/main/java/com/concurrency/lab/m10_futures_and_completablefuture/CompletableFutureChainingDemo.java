package com.concurrency.lab.m10_futures_and_completablefuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompletableFutureChainingDemo {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        System.out.println("== thenApply: transform the result, stays on the completing thread ==");
        CompletableFuture<Integer> applied = CompletableFuture
                .supplyAsync(() -> 10, executor)
                .thenApply(value -> value * 2);
        System.out.println("thenApply result=" + applied.get());

        System.out.println("== thenCompose: chain to another CompletableFuture-returning step (flatMap) ==");
        CompletableFuture<String> composed = CompletableFuture
                .supplyAsync(() -> 5, executor)
                .thenCompose(value -> CompletableFuture.supplyAsync(() -> "computed:" + (value * value), executor));
        System.out.println("thenCompose result=" + composed.get());

        System.out.println("== thenCombine: merge two independent futures once both complete ==");
        CompletableFuture<Integer> left = CompletableFuture.supplyAsync(() -> 3, executor);
        CompletableFuture<Integer> right = CompletableFuture.supplyAsync(() -> 4, executor);
        CompletableFuture<Integer> combined = left.thenCombine(right, Integer::sum);
        System.out.println("thenCombine result=" + combined.get());

        System.out.println("== thenAccept: consume the result without producing a new value ==");
        CompletableFuture<Void> accepted = CompletableFuture
                .supplyAsync(() -> "accepted-value", executor)
                .thenAccept(value -> System.out.println("thenAccept consumed: " + value));
        accepted.get();

        System.out.println("== thenRun: run a follow-up action, ignoring the prior result entirely ==");
        CompletableFuture<Void> ran = CompletableFuture
                .supplyAsync(() -> "ignored", executor)
                .thenRun(() -> System.out.println("thenRun executed (no access to prior value)"));
        ran.get();

        System.out.println("== *Async variants run the continuation on the supplied Executor, not the caller/completing thread ==");
        CompletableFuture<Void> asyncChain = CompletableFuture
                .supplyAsync(() -> {
                    System.out.println("supplyAsync running on " + Thread.currentThread().getName());
                    return 7;
                }, executor)
                .thenApplyAsync(value -> {
                    System.out.println("thenApplyAsync running on " + Thread.currentThread().getName());
                    return value + 1;
                }, executor)
                .thenAcceptAsync(value -> System.out.println("thenAcceptAsync final value=" + value
                        + " on " + Thread.currentThread().getName()), executor);
        asyncChain.get();

        executor.shutdown();
    }
}
