package com.concurrency.lab.capstone_order_matching_engine;

import java.math.BigDecimal;

public record Trade(long id, String symbol, long buyOrderId, long sellOrderId, BigDecimal price, long quantity,
                     long timestampNanos) {
}
