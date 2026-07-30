package com.concurrency.lab.m16_concurrency_design_patterns;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free token-bucket rate limiter.
 *
 * <p>Bucket state (remaining tokens + last refill timestamp) is held as a single immutable
 * {@link Bucket} behind an {@link AtomicReference}, updated with a compare-and-swap retry loop.
 * See the module README for why this was chosen over a {@code ReentrantLock}-guarded critical
 * section.
 */
public class TokenBucketRateLimiter {

    private final long capacity;
    private final double refillTokensPerNano;
    private final AtomicReference<Bucket> bucketRef;

    public TokenBucketRateLimiter(long capacity, double refillTokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("refillTokensPerSecond must be positive");
        }
        this.capacity = capacity;
        this.refillTokensPerNano = refillTokensPerSecond / 1_000_000_000.0;
        this.bucketRef = new AtomicReference<>(new Bucket(capacity, System.nanoTime()));
    }

    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        while (true) {
            Bucket current = bucketRef.get();
            long now = System.nanoTime();
            double refilled = refill(current, now);

            if (refilled < permits) {
                // publish the refilled-but-insufficient snapshot so the next caller's elapsed-time
                // math starts from `now` instead of re-accumulating the same idle interval
                Bucket drained = new Bucket(refilled, now);
                bucketRef.compareAndSet(current, drained);
                return false;
            }

            Bucket updated = new Bucket(refilled - permits, now);
            if (bucketRef.compareAndSet(current, updated)) {
                return true;
            }
            // lost the race with another acquirer; retry with a fresh snapshot
        }
    }

    public long availableTokens() {
        Bucket current = bucketRef.get();
        return (long) refill(current, System.nanoTime());
    }

    private double refill(Bucket bucket, long now) {
        double elapsedNanos = Math.max(0, now - bucket.lastRefillNanos());
        return Math.min(capacity, bucket.tokens() + elapsedNanos * refillTokensPerNano);
    }

    private record Bucket(double tokens, long lastRefillNanos) {
    }
}
