package com.concurrency.lab.capstone_order_matching_engine;

import java.util.List;

public record BookSnapshot(String symbol, List<PriceLevel> bids, List<PriceLevel> asks) {
}
