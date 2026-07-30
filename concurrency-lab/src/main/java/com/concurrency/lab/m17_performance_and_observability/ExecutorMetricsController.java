package com.concurrency.lab.m17_performance_and_observability;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes live stats for an internally-managed {@link ThreadPoolExecutor} that is continuously
 * fed a light synthetic workload, so the numbers returned by {@code /pool-stats} actually move.
 * In a real service these same {@link ThreadPoolExecutor} getters are what you would wire into
 * Micrometer {@code Gauge}s (registered against the executor bean) so they surface automatically
 * on the Actuator {@code /actuator/metrics} and {@code /actuator/prometheus} endpoints.
 */
@RestController
@RequestMapping("/api/observability")
public class ExecutorMetricsController {

    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService feeder;

    public ExecutorMetricsController(ThreadPoolExecutor executor) {
        this.executor = executor;
        this.feeder = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pool-stats-feeder");
            t.setDaemon(true);
            return t;
        });
        feeder.scheduleAtFixedRate(this::submitSyntheticTask, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void submitSyntheticTask() {
        try {
            executor.submit(() -> {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } catch (RejectedExecutionException ignored) {
            // queue briefly full under load; the next scheduled tick will try again
        }
    }

    @GetMapping("/pool-stats")
    public PoolStats poolStats() {
        return new PoolStats(
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount());
    }

    public record PoolStats(int poolSize, int activeCount, int queueSize, long completedTaskCount) {
    }

    @PreDestroy
    void shutdownFeeder() {
        feeder.shutdownNow();
    }
}
