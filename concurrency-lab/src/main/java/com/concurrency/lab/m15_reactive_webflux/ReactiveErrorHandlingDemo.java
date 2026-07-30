package com.concurrency.lab.m15_reactive_webflux;

import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ReactiveErrorHandlingDemo {

    public static void main(String[] args) {
        System.out.println("== onErrorResume: switch to a fallback publisher ==");
        Flux.range(1, 5)
                .map(ReactiveErrorHandlingDemo::failOnFour)
                .onErrorResume(e -> {
                    System.out.println("recovering with fallback flux after: " + e);
                    return Flux.just(-1, -2);
                })
                .toIterable()
                .forEach(n -> System.out.println("onErrorResume result: " + n));

        System.out.println("\n== onErrorReturn: substitute a single fallback value ==");
        Flux.range(1, 5)
                .map(ReactiveErrorHandlingDemo::failOnFour)
                .onErrorReturn(-99)
                .toIterable()
                .forEach(n -> System.out.println("onErrorReturn result: " + n));

        System.out.println("\n== retry(n): resubscribe from the start, up to n times ==");
        AtomicInteger attempt = new AtomicInteger();
        try {
            Flux.range(1, 5)
                    .map(i -> {
                        if (attempt.get() < 2 && i == 4) {
                            throw new IllegalStateException("transient failure on attempt " + attempt.get());
                        }
                        return i;
                    })
                    .doOnSubscribe(s -> attempt.incrementAndGet())
                    .retry(3)
                    .toIterable()
                    .forEach(n -> System.out.println("retry result: " + n));
        } catch (Exception e) {
            System.out.println("retry(n) exhausted or failed: " + e);
        }

        System.out.println("\n== retryWhen: bounded exponential backoff, giving up after the budget ==");
        AtomicInteger permanentAttempt = new AtomicInteger();
        try {
            Flux.range(1, 5)
                    .map(i -> {
                        permanentAttempt.incrementAndGet();
                        if (i == 3) {
                            throw new IllegalStateException("permanent failure at i=3, attempt " + permanentAttempt.get());
                        }
                        return i;
                    })
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(10)))
                    .toIterable()
                    .forEach(n -> System.out.println("retryWhen result: " + n));
        } catch (Exception e) {
            System.out.println("retryWhen gave up after its retry budget: " + e);
        }
    }

    private static int failOnFour(int value) {
        if (value == 4) {
            throw new RuntimeException("simulated failure at value=" + value);
        }
        return value;
    }
}
