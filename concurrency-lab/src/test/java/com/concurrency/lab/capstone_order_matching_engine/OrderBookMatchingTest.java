package com.concurrency.lab.capstone_order_matching_engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookMatchingTest {

    @Test
    void restsWhenNoOppositeOrdersExist() {
        OrderBook book = new OrderBook();

        List<Trade> trades = book.match(new Order(1, "AAPL", Side.BUY, BigDecimal.valueOf(100), 10, System.nanoTime()));

        assertThat(trades).isEmpty();
        assertThat(book.snapshot("AAPL", 5).bids()).hasSize(1);
    }

    @Test
    void matchesCrossingOrderFullyAtTheRestingPrice() {
        OrderBook book = new OrderBook();
        book.match(new Order(1, "AAPL", Side.SELL, BigDecimal.valueOf(100), 10, System.nanoTime()));

        List<Trade> trades = book.match(new Order(2, "AAPL", Side.BUY, BigDecimal.valueOf(101), 10, System.nanoTime()));

        assertThat(trades).hasSize(1);
        Trade trade = trades.get(0);
        assertThat(trade.price()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(trade.quantity()).isEqualTo(10);
        assertThat(trade.buyOrderId()).isEqualTo(2);
        assertThat(trade.sellOrderId()).isEqualTo(1);
        assertThat(book.snapshot("AAPL", 5).asks()).isEmpty();
    }

    @Test
    void partiallyFillsAndRestsTheRemainder() {
        OrderBook book = new OrderBook();
        book.match(new Order(1, "AAPL", Side.SELL, BigDecimal.valueOf(100), 5, System.nanoTime()));

        List<Trade> trades = book.match(new Order(2, "AAPL", Side.BUY, BigDecimal.valueOf(100), 8, System.nanoTime()));

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).quantity()).isEqualTo(5);
        assertThat(book.snapshot("AAPL", 5).bids())
                .singleElement()
                .satisfies(level -> assertThat(level.totalQuantity()).isEqualTo(3));
    }

    @Test
    void doesNotCrossWhenPricesDoNotOverlap() {
        OrderBook book = new OrderBook();
        book.match(new Order(1, "AAPL", Side.SELL, BigDecimal.valueOf(105), 10, System.nanoTime()));

        List<Trade> trades = book.match(new Order(2, "AAPL", Side.BUY, BigDecimal.valueOf(100), 10, System.nanoTime()));

        assertThat(trades).isEmpty();
        assertThat(book.snapshot("AAPL", 5).bids()).hasSize(1);
        assertThat(book.snapshot("AAPL", 5).asks()).hasSize(1);
    }

    @Test
    void walksMultiplePriceLevelsUntilFilled() {
        OrderBook book = new OrderBook();
        book.match(new Order(1, "AAPL", Side.SELL, BigDecimal.valueOf(100), 5, System.nanoTime()));
        book.match(new Order(2, "AAPL", Side.SELL, BigDecimal.valueOf(101), 5, System.nanoTime()));

        List<Trade> trades = book.match(new Order(3, "AAPL", Side.BUY, BigDecimal.valueOf(101), 10, System.nanoTime()));

        assertThat(trades).hasSize(2);
        assertThat(trades.get(0).price()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(trades.get(1).price()).isEqualByComparingTo(BigDecimal.valueOf(101));
        assertThat(book.snapshot("AAPL", 5).asks()).isEmpty();
    }
}
