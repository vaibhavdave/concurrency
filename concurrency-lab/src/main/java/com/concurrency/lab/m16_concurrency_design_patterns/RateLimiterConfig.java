package com.concurrency.lab.m16_concurrency_design_patterns;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RateLimiterConfig {

    @Bean
    TokenBucketRateLimiter tokenBucketRateLimiter() {
        return new TokenBucketRateLimiter(10, 5.0);
    }
}
