package com.concurrency.lab.m10_futures_and_completablefuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompletableFutureExceptionHandlingTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void exceptionallyRecoversFromUpstreamFailure() throws Exception {
        CompletableFuture<Integer> recovered = CompletableFuture
                .<Integer>supplyAsync(() -> {
                    throw new IllegalStateException("boom");
                }, executor)
                .exceptionally(ex -> -1);

        assertThat(recovered.get(5, TimeUnit.SECONDS)).isEqualTo(-1);
    }

    @Test
    void exceptionallyIsSkippedWhenUpstreamSucceeds() throws Exception {
        AtomicBoolean recoveryRan = new AtomicBoolean(false);

        CompletableFuture<Integer> future = CompletableFuture
                .supplyAsync(() -> 42, executor)
                .exceptionally(ex -> {
                    recoveryRan.set(true);
                    return -1;
                });

        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(42);
        assertThat(recoveryRan.get()).isFalse();
    }

    @Test
    void handleRunsOnBothSuccessAndFailurePaths() throws Exception {
        CompletableFuture<String> successPath = CompletableFuture
                .supplyAsync(() -> 21, executor)
                .handle((value, ex) -> ex != null ? "error" : "value:" + value);
        assertThat(successPath.get(5, TimeUnit.SECONDS)).isEqualTo("value:21");

        CompletableFuture<String> failurePath = CompletableFuture
                .<Integer>supplyAsync(() -> {
                    throw new RuntimeException("failure");
                }, executor)
                .handle((value, ex) -> ex != null ? "error" : "value:" + value);
        assertThat(failurePath.get(5, TimeUnit.SECONDS)).isEqualTo("error");
    }

    @Test
    void whenCompleteObservesFailureButDoesNotRecoverTheOutcome() throws Exception {
        AtomicReference<Throwable> observed = new AtomicReference<>();

        CompletableFuture<Integer> future = CompletableFuture
                .<Integer>supplyAsync(() -> {
                    throw new RuntimeException("still-fails");
                }, executor)
                .whenComplete((value, ex) -> observed.set(ex));

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseMessage("still-fails");
        assertThat(observed.get()).isNotNull();
    }

    @Test
    void failureShortCircuitsDownstreamThenApplyUntilRecovered() throws Exception {
        AtomicBoolean downstreamRan = new AtomicBoolean(false);

        CompletableFuture<Integer> chain = CompletableFuture
                .supplyAsync(() -> 10, executor)
                .<Integer>thenApply(value -> {
                    throw new RuntimeException("mid-chain failure");
                })
                .thenApply(value -> {
                    downstreamRan.set(true);
                    return value;
                })
                .exceptionally(ex -> -999);

        assertThat(chain.get(5, TimeUnit.SECONDS)).isEqualTo(-999);
        assertThat(downstreamRan.get()).isFalse();
    }
}
