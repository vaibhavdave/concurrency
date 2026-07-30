package com.concurrency.lab.capstone_order_matching_engine;

import java.math.BigDecimal;

public record PriceLevel(BigDecimal price, long totalQuantity, int orderCount) {
}
