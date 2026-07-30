package com.concurrency.lab.m16_concurrency_design_patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    private static final Callable<String> ALWAYS_FAILS = () -> {
        throw new RuntimeException("boom");
    };
    private static final Callable<String> ALWAYS_SUCCEEDS = () -> "ok";

    @Test
    void opensAfterConsecutiveFailuresReachThreshold() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofMinutes(1));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> breaker.call(ALWAYS_FAILS)).isInstanceOf(RuntimeException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void rejectsCallsImmediatelyWhileOpen() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMinutes(1));
        assertThatThrownBy(() -> breaker.call(ALWAYS_FAILS)).isInstanceOf(RuntimeException.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> breaker.call(ALWAYS_SUCCEEDS))
                .isInstanceOf(CircuitBreaker.CircuitOpenException.class);
    }

    @Test
    void transitionsToHalfOpenAfterCooldownThenClosesOnTrialSuccess() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMillis(100));
        assertThatThrownBy(() -> breaker.call(ALWAYS_FAILS)).isInstanceOf(RuntimeException.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> breaker.getState() == CircuitBreaker.State.HALF_OPEN);

        String result = breaker.call(ALWAYS_SUCCEEDS);

        assertThat(result).isEqualTo("ok");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void trialFailureInHalfOpenReopensTheCircuit() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMillis(100));
        assertThatThrownBy(() -> breaker.call(ALWAYS_FAILS)).isInstanceOf(RuntimeException.class);

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> breaker.getState() == CircuitBreaker.State.HALF_OPEN);

        assertThatThrownBy(() -> breaker.call(ALWAYS_FAILS)).isInstanceOf(RuntimeException.class);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void successfulCallsInClosedStateNeverOpenTheCircuit() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(2, Duration.ofMinutes(1));
        for (int i = 0; i < 10; i++) {
            assertThat(breaker.call(ALWAYS_SUCCEEDS)).isEqualTo("ok");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
