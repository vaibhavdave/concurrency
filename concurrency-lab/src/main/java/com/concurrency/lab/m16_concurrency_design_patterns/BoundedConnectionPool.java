package com.concurrency.lab.m16_concurrency_design_patterns;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A fixed-size pool of simulated "connections". A {@link Semaphore} bounds how many callers may
 * hold a connection concurrently; a {@link BlockingQueue} hands idle instances back out. The
 * semaphore permit count and the queue size are kept in lockstep: a permit is only ever granted
 * once the corresponding connection has been placed in the queue, so {@code idleConnections.poll()}
 * right after acquiring a permit can never race against an empty queue.
 */
public class BoundedConnectionPool {

    public record Connection(String id) {
    }

    private final Semaphore permits;
    private final BlockingQueue<Connection> idleConnections;

    public BoundedConnectionPool(int poolSize) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be positive");
        }
        this.permits = new Semaphore(poolSize, true);
        this.idleConnections = new LinkedBlockingQueue<>();
        for (int i = 0; i < poolSize; i++) {
            idleConnections.offer(new Connection("conn-" + i));
        }
    }

    public Connection borrow() throws InterruptedException {
        permits.acquire();
        Connection connection = idleConnections.poll();
        if (connection == null) {
            // should be unreachable given the permit/queue invariant above
            throw new IllegalStateException("acquired a permit but no idle connection was available");
        }
        return connection;
    }

    public Optional<Connection> tryBorrow(long timeout, TimeUnit unit) throws InterruptedException {
        if (!permits.tryAcquire(timeout, unit)) {
            return Optional.empty();
        }
        return Optional.ofNullable(idleConnections.poll());
    }

    public void release(Connection connection) {
        idleConnections.offer(connection);
        permits.release();
    }

    public int availablePermits() {
        return permits.availablePermits();
    }

    public int queuedThreadCount() {
        return permits.getQueueLength();
    }
}
