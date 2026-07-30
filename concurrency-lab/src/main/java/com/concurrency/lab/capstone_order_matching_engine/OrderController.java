package com.concurrency.lab.capstone_order_matching_engine;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final MatchingEngine matchingEngine;

    public OrderController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> submit(@RequestBody OrderRequest request) {
        try {
            long orderId = matchingEngine.submit(request.symbol(), request.side(), request.price(), request.quantity());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OrderResponse(orderId, "ACCEPTED"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new OrderResponse(-1, "REJECTED_INTERRUPTED"));
        }
    }

    @GetMapping("/book/{symbol}")
    public BookSnapshot book(@PathVariable String symbol, @RequestParam(defaultValue = "10") int depth) {
        return matchingEngine.snapshot(symbol, depth);
    }

    @GetMapping("/trades/{symbol}")
    public List<Trade> trades(@PathVariable String symbol) {
        return matchingEngine.recentTrades(symbol);
    }

    @GetMapping("/stats")
    public EngineStats stats() {
        return matchingEngine.stats();
    }

    record OrderRequest(String symbol, Side side, BigDecimal price, long quantity) {
    }

    record OrderResponse(long orderId, String status) {
    }
}
