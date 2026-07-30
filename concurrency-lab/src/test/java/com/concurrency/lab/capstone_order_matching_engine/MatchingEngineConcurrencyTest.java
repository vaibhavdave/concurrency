package com.concurrency.lab.capstone_order_matching_engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MatchingEngineConcurrencyTest {

    @Test
    void processesEveryConcurrentlySubmittedOrderExactlyOnce() throws InterruptedException {
        MatchingEngine engine = new MatchingEngine();
        int totalOrders = 2_000;
        CountDownLatch submitted = new CountDownLatch(totalOrders);
        AtomicInteger failures = new AtomicInteger();

        try (ExecutorService submitters = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalOrders; i++) {
                submitters.submit(() -> {
                    try {
                        ThreadLocalRandom random = ThreadLocalRandom.current();
                        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
                        engine.submit("TEST", side, BigDecimal.valueOf(100 + random.nextInt(5)), 1 + random.nextInt(10));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failures.incrementAndGet();
                    } finally {
                        submitted.countDown();
                    }
                });
            }
            submitted.await();
        }

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(engine.stats().totalOrdersProcessed()).isEqualTo(totalOrders));

        assertThat(failures.get()).isZero();
        assertThat(engine.stats().activeSymbols()).isEqualTo(1);
    }

    @Test
    void isolatesStateBetweenSymbols() throws InterruptedException {
        MatchingEngine engine = new MatchingEngine();

        engine.submit("AAA", Side.BUY, BigDecimal.valueOf(10), 5);
        engine.submit("BBB", Side.SELL, BigDecimal.valueOf(20), 7);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(engine.stats().totalOrdersProcessed()).isEqualTo(2));

        assertThat(engine.snapshot("AAA", 5).bids()).hasSize(1);
        assertThat(engine.snapshot("AAA", 5).asks()).isEmpty();
        assertThat(engine.snapshot("BBB", 5).asks()).hasSize(1);
        assertThat(engine.snapshot("BBB", 5).bids()).isEmpty();
    }
}
