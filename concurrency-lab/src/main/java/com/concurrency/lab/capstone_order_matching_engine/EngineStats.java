package com.concurrency.lab.capstone_order_matching_engine;

public record EngineStats(int activeSymbols, long totalOrdersProcessed, long totalTradesExecuted) {
}
