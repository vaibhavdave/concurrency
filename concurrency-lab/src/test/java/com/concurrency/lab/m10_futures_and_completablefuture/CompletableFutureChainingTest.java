package com.concurrency.lab.m10_futures_and_completablefuture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompletableFutureChainingTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void thenApplyTransformsResult() throws Exception {
        CompletableFuture<Integer> future = CompletableFuture
                .supplyAsync(() -> 10, executor)
                .thenApply(value -> value * 2);

        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(20);
    }

    @Test
    void thenComposeFlattensNestedFuture() throws Exception {
        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> 5, executor)
                .thenCompose(value -> CompletableFuture.supplyAsync(() -> "value=" + value, executor));

        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("value=5");
    }

    @Test
    void thenCombineMergesTwoIndependentFutures() throws Exception {
        CompletableFuture<Integer> left = CompletableFuture.supplyAsync(() -> 3, executor);
        CompletableFuture<Integer> right = CompletableFuture.supplyAsync(() -> 4, executor);

        CompletableFuture<Integer> combined = left.thenCombine(right, Integer::sum);

        assertThat(combined.get(5, TimeUnit.SECONDS)).isEqualTo(7);
    }

    @Test
    void allOfWaitsForEveryFutureBeforeCompleting() throws Exception {
        CompletableFuture<String> a = CompletableFuture.supplyAsync(() -> "a", executor);
        CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> "b", executor);
        CompletableFuture<String> c = CompletableFuture.supplyAsync(() -> "c", executor);

        CompletableFuture<List<String>> all = CompletableFuture
                .allOf(a, b, c)
                .thenApply(unused -> List.of(a.join(), b.join(), c.join()));

        assertThat(all.get(5, TimeUnit.SECONDS)).containsExactly("a", "b", "c");
    }

    @Test
    void thenApplyAsyncRunsOnSuppliedExecutorNotCallingThread() throws Exception {
        String callingThreadName = Thread.currentThread().getName();

        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> 1, executor)
                .thenApplyAsync(value -> Thread.currentThread().getName(), executor);

        String executionThreadName = future.get(5, TimeUnit.SECONDS);
        assertThat(executionThreadName).isNotEqualTo(callingThreadName);
    }
}
