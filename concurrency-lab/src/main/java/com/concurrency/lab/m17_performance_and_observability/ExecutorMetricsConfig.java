package com.concurrency.lab.m17_performance_and_observability;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ExecutorMetricsConfig {

    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor observabilityExecutor() {
        return new ThreadPoolExecutor(
                4,
                4,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(500));
    }
}
