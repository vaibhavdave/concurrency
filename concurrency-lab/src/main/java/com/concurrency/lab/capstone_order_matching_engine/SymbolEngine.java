package com.concurrency.lab.capstone_order_matching_engine;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

/**
 * One matching "actor" per symbol: a bounded mailbox drained by exactly one
 * dedicated virtual thread. Every other component only ever talks to an
 * OrderBook through this queue, which is what lets OrderBook itself stay
 * completely lock-free (see m16 worker-pool/actor pattern).
 */
final class SymbolEngine {

    private static final int TRADE_HISTORY_LIMIT = 200;

    private final String symbol;
    private final OrderBook book = new OrderBook();
    private final BlockingQueue<Order> inbox;
    private final ConcurrentLinkedDeque<Trade> recentTrades = new ConcurrentLinkedDeque<>();
    private final LongAdder ordersProcessed = new LongAdder();
    private final LongAdder tradesExecuted = new LongAdder();
    private final Thread worker;
    private volatile boolean running = true;

    SymbolEngine(String symbol, int inboxCapacity) {
        this.symbol = symbol;
        this.inbox = new ArrayBlockingQueue<>(inboxCapacity);
        this.worker = Thread.ofVirtual().name("symbol-engine-" + symbol).start(this::processLoop);
    }

    void submit(Order order) throws InterruptedException {
        inbox.put(order);
    }

    private void processLoop() {
        while (running) {
            try {
                Order order = inbox.take();
                List<Trade> trades = book.match(order);
                ordersProcessed.increment();
                if (!trades.isEmpty()) {
                    tradesExecuted.add(trades.size());
                    for (Trade trade : trades) {
                        recentTrades.addLast(trade);
                        while (recentTrades.size() > TRADE_HISTORY_LIMIT) {
                            recentTrades.pollFirst();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    BookSnapshot snapshot(int depth) {
        return book.snapshot(symbol, depth);
    }

    List<Trade> recentTrades() {
        return List.copyOf(recentTrades);
    }

    long ordersProcessed() {
        return ordersProcessed.sum();
    }

    long tradesExecuted() {
        return tradesExecuted.sum();
    }

    void shutdown() {
        running = false;
        worker.interrupt();
    }
}
