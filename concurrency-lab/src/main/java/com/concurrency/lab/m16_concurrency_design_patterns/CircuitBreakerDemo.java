package com.concurrency.lab.m16_concurrency_design_patterns;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class CircuitBreakerDemo {

    public static void main(String[] args) throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofMillis(500));
        AtomicInteger callCount = new AtomicInteger();

        // fails on its first 3 invocations (matching the failure threshold), then succeeds forever after
        FlakyDownstream downstream = new FlakyDownstream(3);

        for (int i = 0; i < 4; i++) {
            invokeAndReport(breaker, downstream, callCount);
        }

        System.out.println("Circuit is now " + breaker.getState() + " - sleeping through the cooldown window");
        Thread.sleep(600);

        System.out.println("After cooldown, state (lazily transitions on next check) = " + breaker.getState());
        invokeAndReport(breaker, downstream, callCount);
        invokeAndReport(breaker, downstream, callCount);
    }

    private static void invokeAndReport(CircuitBreaker breaker, FlakyDownstream downstream, AtomicInteger callCount) {
        int attempt = callCount.incrementAndGet();
        try {
            String result = breaker.call(downstream::call);
            System.out.printf("attempt %d -> SUCCESS (%s), breaker state=%s%n", attempt, result, breaker.getState());
        } catch (CircuitBreaker.CircuitOpenException e) {
            System.out.printf("attempt %d -> REJECTED (circuit open), breaker state=%s%n", attempt, breaker.getState());
        } catch (Exception e) {
            System.out.printf("attempt %d -> FAILURE (%s), breaker state=%s%n", attempt, e.getMessage(), breaker.getState());
        }
    }

    static final class FlakyDownstream {
        private final AtomicInteger calls = new AtomicInteger();
        private final int failFirstNCalls;

        FlakyDownstream(int failFirstNCalls) {
            this.failFirstNCalls = failFirstNCalls;
        }

        String call() throws Exception {
            int n = calls.incrementAndGet();
            if (n <= failFirstNCalls) {
                throw new RuntimeException("downstream unavailable (call " + n + ")");
            }
            return "ok";
        }
    }
}
