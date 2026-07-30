package com.concurrency.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Companion benchmark for m04 (atomic/CAS) and m02 (synchronized): measures
 * how three different ways of incrementing a shared counter scale under
 * contention from many concurrent threads.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CounterContentionBenchmark {

    private long plainCounter = 0;
    private final Object lock = new Object();
    private final AtomicLong atomicCounter = new AtomicLong();
    private final LongAdder longAdder = new LongAdder();

    @Benchmark
    @Threads(8)
    public void synchronizedIncrement() {
        synchronized (lock) {
            plainCounter++;
        }
    }

    @Benchmark
    @Threads(8)
    public void atomicIncrement() {
        atomicCounter.incrementAndGet();
    }

    @Benchmark
    @Threads(8)
    public void longAdderIncrement() {
        longAdder.increment();
    }
}
