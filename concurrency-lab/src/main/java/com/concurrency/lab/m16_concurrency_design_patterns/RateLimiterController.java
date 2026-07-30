package com.concurrency.lab.m16_concurrency_design_patterns;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rate-limiter")
public class RateLimiterController {

    private final TokenBucketRateLimiter rateLimiter;

    public RateLimiterController(TokenBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/try")
    public ResponseEntity<Map<String, Object>> tryAcquire() {
        if (rateLimiter.tryAcquire()) {
            return ResponseEntity.ok(Map.of("allowed", true));
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("allowed", false, "retryAfterHint", "wait for the bucket to refill"));
    }
}
