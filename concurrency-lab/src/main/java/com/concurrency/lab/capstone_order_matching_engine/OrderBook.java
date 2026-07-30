package com.concurrency.lab.capstone_order_matching_engine;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Limit-order book for a single symbol.
 *
 * Deliberately holds no locks and no atomics: correctness relies on the
 * single-writer principle demonstrated in m16 (worker-pool/actor pattern) —
 * a SymbolEngine only ever calls match()/snapshot() from its own dedicated
 * virtual thread, so mutable state here is never touched concurrently.
 */
final class OrderBook {

    private final NavigableMap<BigDecimal, Deque<RestingOrder>> buyLevels = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, Deque<RestingOrder>> sellLevels = new TreeMap<>();
    private long nextTradeId = 1;

    List<Trade> match(Order incoming) {
        List<Trade> trades = new ArrayList<>();
        NavigableMap<BigDecimal, Deque<RestingOrder>> opposite = incoming.side() == Side.BUY ? sellLevels : buyLevels;
        long remaining = incoming.quantity();

        while (remaining > 0 && !opposite.isEmpty()) {
            Map.Entry<BigDecimal, Deque<RestingOrder>> best = opposite.firstEntry();
            BigDecimal levelPrice = best.getKey();
            if (!crosses(incoming.side(), incoming.price(), levelPrice)) {
                break;
            }
            Deque<RestingOrder> queue = best.getValue();
            while (remaining > 0 && !queue.isEmpty()) {
                RestingOrder resting = queue.peekFirst();
                long tradedQuantity = Math.min(remaining, resting.remainingQuantity());
                trades.add(toTrade(incoming, resting, levelPrice, tradedQuantity));
                remaining -= tradedQuantity;
                resting.fill(tradedQuantity);
                if (resting.remainingQuantity() == 0) {
                    queue.pollFirst();
                }
            }
            if (queue.isEmpty()) {
                opposite.remove(levelPrice);
            }
        }

        if (remaining > 0) {
            restingLevels(incoming.side())
                    .computeIfAbsent(incoming.price(), price -> new ArrayDeque<>())
                    .addLast(new RestingOrder(incoming.id(), remaining));
        }
        return trades;
    }

    BookSnapshot snapshot(String symbol, int depth) {
        return new BookSnapshot(symbol, levels(buyLevels, depth), levels(sellLevels, depth));
    }

    private static boolean crosses(Side side, BigDecimal incomingPrice, BigDecimal restingPrice) {
        return side == Side.BUY
                ? incomingPrice.compareTo(restingPrice) >= 0
                : incomingPrice.compareTo(restingPrice) <= 0;
    }

    private NavigableMap<BigDecimal, Deque<RestingOrder>> restingLevels(Side side) {
        return side == Side.BUY ? buyLevels : sellLevels;
    }

    private Trade toTrade(Order incoming, RestingOrder resting, BigDecimal price, long quantity) {
        long buyOrderId = incoming.side() == Side.BUY ? incoming.id() : resting.orderId();
        long sellOrderId = incoming.side() == Side.SELL ? incoming.id() : resting.orderId();
        return new Trade(nextTradeId++, incoming.symbol(), buyOrderId, sellOrderId, price, quantity, System.nanoTime());
    }

    private static List<PriceLevel> levels(NavigableMap<BigDecimal, Deque<RestingOrder>> levels, int depth) {
        List<PriceLevel> result = new ArrayList<>();
        for (Map.Entry<BigDecimal, Deque<RestingOrder>> entry : levels.entrySet()) {
            if (result.size() >= depth) {
                break;
            }
            long totalQuantity = entry.getValue().stream().mapToLong(RestingOrder::remainingQuantity).sum();
            result.add(new PriceLevel(entry.getKey(), totalQuantity, entry.getValue().size()));
        }
        return result;
    }

    private static final class RestingOrder {
        private final long orderId;
        private long remainingQuantity;

        RestingOrder(long orderId, long remainingQuantity) {
            this.orderId = orderId;
            this.remainingQuantity = remainingQuantity;
        }

        long orderId() {
            return orderId;
        }

        long remainingQuantity() {
            return remainingQuantity;
        }

        void fill(long quantity) {
            remainingQuantity -= quantity;
        }
    }
}
