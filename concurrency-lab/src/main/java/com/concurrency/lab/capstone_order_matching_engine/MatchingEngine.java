package com.concurrency.lab.capstone_order_matching_engine;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MatchingEngine {

    private static final int DEFAULT_INBOX_CAPACITY = 10_000;

    private final Map<String, SymbolEngine> engines = new ConcurrentHashMap<>();
    private final AtomicLong orderIdGenerator = new AtomicLong();

    public long submit(String symbol, Side side, BigDecimal price, long quantity) throws InterruptedException {
        long orderId = orderIdGenerator.incrementAndGet();
        Order order = new Order(orderId, symbol, side, price, quantity, System.nanoTime());
        engineFor(symbol).submit(order);
        return orderId;
    }

    public BookSnapshot snapshot(String symbol, int depth) {
        SymbolEngine engine = engines.get(symbol);
        return engine == null ? new BookSnapshot(symbol, List.of(), List.of()) : engine.snapshot(depth);
    }

    public List<Trade> recentTrades(String symbol) {
        SymbolEngine engine = engines.get(symbol);
        return engine == null ? List.of() : engine.recentTrades();
    }

    public EngineStats stats() {
        long orders = 0;
        long trades = 0;
        for (SymbolEngine engine : engines.values()) {
            orders += engine.ordersProcessed();
            trades += engine.tradesExecuted();
        }
        return new EngineStats(engines.size(), orders, trades);
    }

    private SymbolEngine engineFor(String symbol) {
        // computeIfAbsent holds the ConcurrentHashMap bin lock while the mapping
        // function runs (see m06's compound-action pitfalls). SymbolEngine's
        // constructor only starts a virtual thread -- cheap and non-blocking --
        // so this stays safe; a slow mapping function here would serialize
        // unrelated symbols that happen to land on the same bin.
        return engines.computeIfAbsent(symbol, s -> new SymbolEngine(s, DEFAULT_INBOX_CAPACITY));
    }

    @PreDestroy
    void shutdown() {
        engines.values().forEach(SymbolEngine::shutdown);
    }
}
