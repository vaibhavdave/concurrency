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
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

/**
 * Companion benchmark for m05 (explicit locks): a slightly larger critical
 * section (two related field updates) guarded three different ways, under
 * contention from many concurrent threads.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class LockContentionBenchmark {

    private long sum = 0;
    private long count = 0;
    private final Object intrinsicLock = new Object();
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final StampedLock stampedLock = new StampedLock();

    @Benchmark
    @Threads(8)
    public void synchronizedUpdate() {
        synchronized (intrinsicLock) {
            sum += 1;
            count += 1;
        }
    }

    @Benchmark
    @Threads(8)
    public void reentrantLockUpdate() {
        reentrantLock.lock();
        try {
            sum += 1;
            count += 1;
        } finally {
            reentrantLock.unlock();
        }
    }

    @Benchmark
    @Threads(8)
    public void stampedLockWriteUpdate() {
        long stamp = stampedLock.writeLock();
        try {
            sum += 1;
            count += 1;
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }
}
