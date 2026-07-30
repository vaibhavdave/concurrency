package com.concurrency.lab.m15_reactive_webflux;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;

public class ReactiveVsBlockingComparisonDemo {

    private static final int CALL_COUNT = 20;
    private static final long SIMULATED_LATENCY_MILLIS = 100;
    private static final int CONCURRENCY = 20;

    public static void main(String[] args) {
        System.out.println("== Blocking, sequential calls to a slow downstream service ==");
        Instant blockingStart = Instant.now();
        for (int i = 0; i < CALL_COUNT; i++) {
            callSlowServiceBlocking(i);
        }
        Duration blockingElapsed = Duration.between(blockingStart, Instant.now());
        System.out.println("Blocking total: " + blockingElapsed.toMillis() + " ms for " + CALL_COUNT + " calls");

        System.out.println("\n== Reactive, concurrent calls via flatMap + boundedElastic ==");
        Instant reactiveStart = Instant.now();
        Flux.range(0, CALL_COUNT)
                .flatMap(id -> callSlowServiceReactive(id), CONCURRENCY)
                .collectList()
                .block();
        Duration reactiveElapsed = Duration.between(reactiveStart, Instant.now());
        System.out.println("Reactive total: " + reactiveElapsed.toMillis() + " ms for " + CALL_COUNT + " calls (concurrency=" + CONCURRENCY + ")");

        System.out.println("\nThe blocking loop pays the full " + SIMULATED_LATENCY_MILLIS + " ms latency " + CALL_COUNT
                + " times sequentially. flatMap(..., concurrency) fans the same calls out onto up to "
                + CONCURRENCY + " concurrent subscriptions, so the wall-clock time approaches a single call's latency instead of the sum.");
    }

    private static void callSlowServiceBlocking(int id) {
        try {
            Thread.sleep(SIMULATED_LATENCY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Mono<Integer> callSlowServiceReactive(int id) {
        // The simulated downstream call is itself a blocking Thread.sleep, so it must
        // run on boundedElastic (a pool sized for blocking work) rather than on a
        // regular reactive scheduler (parallel/single), which are meant to stay
        // non-blocking and would otherwise starve other reactive work sharing them.
        return Mono.fromCallable(() -> {
                    Thread.sleep(SIMULATED_LATENCY_MILLIS);
                    return id;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
