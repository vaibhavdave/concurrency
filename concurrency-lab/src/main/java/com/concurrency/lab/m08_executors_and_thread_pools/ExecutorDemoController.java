package com.concurrency.lab.m08_executors_and_thread_pools;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/executors")
public class ExecutorDemoController {

    @PostMapping("/simulate")
    public SimulationResult simulate(
            @RequestParam(defaultValue = "100") int tasks,
            @RequestParam(defaultValue = "8") int poolSize,
            @RequestParam(defaultValue = "50") long workMillis) throws InterruptedException {

        // bounded queue (2x pool size) so a burst of tasks larger than pool+queue capacity
        // demonstrates real AbortPolicy rejections rather than silently absorbing everything
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(poolSize * 2),
                new ThreadPoolExecutor.AbortPolicy());

        AtomicInteger completed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch allSubmittedTasksDone = new CountDownLatch(tasks);

        long start = System.currentTimeMillis();
        for (int i = 0; i < tasks; i++) {
            try {
                pool.execute(() -> {
                    try {
                        Thread.sleep(workMillis);
                        completed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        allSubmittedTasksDone.countDown();
                    }
                });
            } catch (RejectedExecutionException e) {
                rejected.incrementAndGet();
                allSubmittedTasksDone.countDown();
            }
        }

        allSubmittedTasksDone.await(30, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        int activeCount = pool.getActiveCount();
        int queueSize = pool.getQueue().size();

        pool.shutdown();

        return new SimulationResult(tasks, completed.get(), rejected.get(), elapsed, activeCount, queueSize);
    }

    public record SimulationResult(
            int submitted,
            int completed,
            int rejected,
            long elapsedMillis,
            int activeCount,
            int queueSize) {
    }
}
