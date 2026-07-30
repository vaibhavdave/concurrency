package com.concurrency.lab.m16_concurrency_design_patterns;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal thread-safe circuit breaker: CLOSED -&gt; OPEN after {@code failureThreshold}
 * consecutive failures -&gt; HALF_OPEN after {@code openDuration} has elapsed -&gt; CLOSED on a
 * successful trial call (or back to OPEN on a failed trial).
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration openDuration;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAtNanos = new AtomicLong();
    private final AtomicBoolean halfOpenTrialInFlight = new AtomicBoolean(false);

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    public State getState() {
        transitionToHalfOpenIfCooldownElapsed();
        return state.get();
    }

    public <T> T call(Callable<T> downstream) throws Exception {
        State current = getState();

        if (current == State.OPEN) {
            throw new CircuitOpenException();
        }

        if (current == State.HALF_OPEN) {
            // CAS ensures exactly one concurrent caller wins the right to run the trial call;
            // everyone else is rejected until that trial resolves
            if (!halfOpenTrialInFlight.compareAndSet(false, true)) {
                throw new CircuitOpenException();
            }
            try {
                T result = downstream.call();
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure();
                throw e;
            } finally {
                halfOpenTrialInFlight.set(false);
            }
        }

        try {
            T result = downstream.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    private void transitionToHalfOpenIfCooldownElapsed() {
        if (state.get() == State.OPEN) {
            long elapsed = System.nanoTime() - openedAtNanos.get();
            if (elapsed >= openDuration.toNanos()) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
            }
        }
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    private void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state.get() == State.HALF_OPEN || failures >= failureThreshold) {
            openCircuit();
        }
    }

    private void openCircuit() {
        consecutiveFailures.set(0);
        openedAtNanos.set(System.nanoTime());
        state.set(State.OPEN);
    }

    public static class CircuitOpenException extends RuntimeException {
        public CircuitOpenException() {
            super("circuit breaker is OPEN");
        }
    }
}
