package com.concurrency.lab.m16_concurrency_design_patterns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void allowsBurstUpToCapacityThenDeniesUntilRefill() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 5.0);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire()).as("permit #%d within capacity", i).isTrue();
        }
        assertThat(limiter.tryAcquire()).as("capacity exhausted").isFalse();

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(limiter::tryAcquire);
    }

    @Test
    void neverGrantsMorePermitsThanCapacityUnderConcurrentContention() throws InterruptedException {
        // Refill rate is deliberately tiny (not zero, since the constructor requires a positive
        // rate): the test's whole run comfortably fits inside a fraction of a token's worth of
        // refill, so capacity alone -- not an in-flight refill -- bounds how many permits can be
        // granted. A high refill rate here would let the bucket top back up *during* the
        // contention window and legitimately grant more than `capacity` permits, which is not a
        // bug in the limiter, just the wrong scenario for asserting a hard capacity ceiling.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(20, 0.001);
        int threadCount = 50;
        AtomicInteger granted = new AtomicInteger();
        CountDownLatch startingGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startingGun.await();
                    if (limiter.tryAcquire()) {
                        granted.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startingGun.countDown();
        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(granted.get()).isLessThanOrEqualTo(20);
    }

    @Test
    void rejectsNonPositiveConstructorArguments() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TokenBucketRateLimiter(0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TokenBucketRateLimiter(1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void availableTokensReflectsRemainingCapacityAfterAcquisitions() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 0.001);

        assertThat(limiter.availableTokens()).isEqualTo(3);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.availableTokens()).isEqualTo(2);
    }
}
