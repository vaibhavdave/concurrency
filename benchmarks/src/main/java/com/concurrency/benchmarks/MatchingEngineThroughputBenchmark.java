package com.concurrency.benchmarks;

import com.concurrency.lab.capstone_order_matching_engine.MatchingEngine;
import com.concurrency.lab.capstone_order_matching_engine.Side;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Companion benchmark for the capstone: sustained concurrent order-submission
 * throughput against the actor-per-symbol MatchingEngine. Every submitting
 * thread targets the same symbol on purpose, so this measures the mailbox's
 * (ArrayBlockingQueue) submission throughput under real contention, not
 * throughput inflated by spreading work across independent symbols.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MatchingEngineThroughputBenchmark {

    private MatchingEngine engine;

    @Setup(Level.Iteration)
    public void setUp() {
        engine = new MatchingEngine();
    }

    @Benchmark
    @Threads(16)
    public void submitOrder() throws InterruptedException {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        engine.submit("BENCH", side, BigDecimal.valueOf(100 + random.nextInt(10)), 1 + random.nextInt(20));
    }
}
