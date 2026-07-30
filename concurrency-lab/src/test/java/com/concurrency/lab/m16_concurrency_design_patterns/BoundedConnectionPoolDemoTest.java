package com.concurrency.lab.m16_concurrency_design_patterns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class BoundedConnectionPoolDemoTest {

    @Test
    void borrowBlocksUntilAnotherThreadReleasesAPermit() throws InterruptedException {
        BoundedConnectionPool pool = new BoundedConnectionPool(1);
        BoundedConnectionPool.Connection first = pool.borrow();
        assertThat(pool.availablePermits()).isZero();

        AtomicReference<BoundedConnectionPool.Connection> secondBorrowResult = new AtomicReference<>();
        CountDownLatch secondBorrowed = new CountDownLatch(1);

        Thread borrower = new Thread(() -> {
            try {
                secondBorrowResult.set(pool.borrow());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                secondBorrowed.countDown();
            }
        });
        borrower.start();

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> pool.queuedThreadCount() == 1);
        assertThat(secondBorrowResult.get()).isNull();

        pool.release(first);

        assertThat(secondBorrowed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(secondBorrowResult.get()).isEqualTo(first);
        borrower.join(2000);
    }

    @Test
    void tryBorrowTimesOutWhenPoolIsExhausted() throws InterruptedException {
        BoundedConnectionPool pool = new BoundedConnectionPool(1);
        pool.borrow();

        assertThat(pool.tryBorrow(100, TimeUnit.MILLISECONDS)).isEmpty();
    }

    @Test
    void releasedConnectionCanBeBorrowedAgain() throws InterruptedException {
        BoundedConnectionPool pool = new BoundedConnectionPool(2);
        BoundedConnectionPool.Connection a = pool.borrow();
        BoundedConnectionPool.Connection b = pool.borrow();
        assertThat(pool.availablePermits()).isZero();

        pool.release(a);
        assertThat(pool.availablePermits()).isEqualTo(1);

        BoundedConnectionPool.Connection reacquired = pool.borrow();
        assertThat(reacquired).isEqualTo(a);

        pool.release(b);
        pool.release(reacquired);
        assertThat(pool.availablePermits()).isEqualTo(2);
    }
}
