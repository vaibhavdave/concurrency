package com.concurrency.lab.capstone_order_matching_engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Standalone load test: hammers a MatchingEngine with concurrent orders from
 * thousands of virtual threads and reports submission vs. end-to-end
 * processing throughput.
 */
public final class LoadGeneratorDemo {

    private static final List<String> SYMBOLS = List.of("BTCUSD", "ETHUSD", "AAPL");
    private static final int TOTAL_ORDERS = 50_000;

    public static void main(String[] args) throws InterruptedException {
        MatchingEngine engine = new MatchingEngine();
        CountDownLatch submitted = new CountDownLatch(TOTAL_ORDERS);

        long start = System.nanoTime();
        try (ExecutorService submitters = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TOTAL_ORDERS; i++) {
                submitters.submit(() -> {
                    try {
                        placeRandomOrder(engine);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        submitted.countDown();
                    }
                });
            }
            submitted.await();
        }
        long submitMillis = elapsedMillis(start);

        while (engine.stats().totalOrdersProcessed() < TOTAL_ORDERS) {
            Thread.sleep(10);
        }
        long totalMillis = elapsedMillis(start);

        EngineStats stats = engine.stats();
        System.out.printf("Submitted %d orders across %d symbols%n", TOTAL_ORDERS, SYMBOLS.size());
        System.out.printf("Submission time:        %d ms (%.0f orders/sec)%n",
                submitMillis, TOTAL_ORDERS * 1000.0 / Math.max(submitMillis, 1));
        System.out.printf("End-to-end drain time:  %d ms (%.0f orders/sec)%n",
                totalMillis, TOTAL_ORDERS * 1000.0 / Math.max(totalMillis, 1));
        System.out.printf("Orders processed: %d, Trades executed: %d%n",
                stats.totalOrdersProcessed(), stats.totalTradesExecuted());

        for (String symbol : SYMBOLS) {
            System.out.println(symbol + " book: " + engine.snapshot(symbol, 3));
        }
    }

    private static void placeRandomOrder(MatchingEngine engine) throws InterruptedException {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String symbol = SYMBOLS.get(random.nextInt(SYMBOLS.size()));
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        BigDecimal price = BigDecimal.valueOf(100 + random.nextInt(50));
        long quantity = 1 + random.nextInt(20);
        engine.submit(symbol, side, price, quantity);
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
