package com.concurrency.lab.capstone_order_matching_engine;

import java.math.BigDecimal;

public record Order(long id, String symbol, Side side, BigDecimal price, long quantity, long timestampNanos) {
}
