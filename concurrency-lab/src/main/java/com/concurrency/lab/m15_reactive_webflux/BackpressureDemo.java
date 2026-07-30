package com.concurrency.lab.m15_reactive_webflux;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BackpressureDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== 1. Naive subscriber that under-requests gets an overflow error ==");
        naiveSubscriberOverflows();

        System.out.println("\n== 2. BaseSubscriber pulling with request(n) keeps up, no overflow ==");
        controlledPullingSubscriber();

        System.out.println("\n== 3. onBackpressureDrop: excess elements are silently dropped ==");
        onBackpressureDropDemo();

        System.out.println("\n== 4. onBackpressureBuffer: excess elements are buffered up to a limit, then oldest is evicted ==");
        onBackpressureBufferDemo();
    }

    private static void naiveSubscriberOverflows() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        // Flux.interval ticks on its own timer regardless of downstream demand; if a
        // subscriber requests a small, fixed amount and never replenishes it, the
        // producer eventually has nowhere to put the next tick and errors out.
        Flux.interval(Duration.ofMillis(2))
                .take(50)
                .subscribe(new BaseSubscriber<Long>() {
                    @Override
                    protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                        System.out.println("naive subscriber requests only 5 elements, once, up front");
                        subscription.request(5);
                    }

                    @Override
                    protected void hookOnNext(Long value) {
                        System.out.println("naive received: " + value);
                    }

                    @Override
                    protected void hookOnError(Throwable throwable) {
                        System.out.println("naive subscriber failed as expected: " + throwable);
                        done.countDown();
                    }

                    @Override
                    protected void hookOnComplete() {
                        System.out.println("naive subscriber completed without overflow (unexpectedly fast run)");
                        done.countDown();
                    }
                });
        done.await(5, TimeUnit.SECONDS);
    }

    private static void controlledPullingSubscriber() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();
        // Ticks arrive every 5ms; simulated processing takes 1ms, so requesting one
        // more element per completed element (a pull, not a push) always keeps
        // outstanding demand ahead of production — no overflow is possible.
        Flux.interval(Duration.ofMillis(5))
                .take(20)
                .subscribe(new BaseSubscriber<Long>() {
                    @Override
                    protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    protected void hookOnNext(Long value) {
                        simulateSlowWork(1);
                        received.incrementAndGet();
                        System.out.println("pulled and processed: " + value);
                        request(1);
                    }

                    @Override
                    protected void hookOnComplete() {
                        System.out.println("controlled subscriber completed cleanly, processed " + received.get() + " elements");
                        done.countDown();
                    }

                    @Override
                    protected void hookOnError(Throwable throwable) {
                        System.out.println("unexpected error: " + throwable);
                        done.countDown();
                    }
                });
        done.await(5, TimeUnit.SECONDS);
    }

    private static void onBackpressureDropDemo() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger dropped = new AtomicInteger();
        Flux.interval(Duration.ofMillis(1))
                .take(100)
                .onBackpressureDrop(value -> dropped.incrementAndGet())
                .subscribe(new BaseSubscriber<Long>() {
                    @Override
                    protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    protected void hookOnNext(Long value) {
                        simulateSlowWork(10);
                        System.out.println("processed (rest dropped in between): " + value);
                        request(1);
                    }

                    @Override
                    protected void hookOnComplete() {
                        System.out.println("stream completed, dropped " + dropped.get() + " elements the slow consumer could not keep up with");
                        done.countDown();
                    }
                });
        done.await(5, TimeUnit.SECONDS);
    }

    private static void onBackpressureBufferDemo() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger evicted = new AtomicInteger();
        int bufferCapacity = 16;
        Flux.interval(Duration.ofMillis(1))
                .take(100)
                .onBackpressureBuffer(
                        bufferCapacity,
                        value -> evicted.incrementAndGet(),
                        BufferOverflowStrategy.DROP_OLDEST)
                .subscribe(new BaseSubscriber<Long>() {
                    @Override
                    protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    protected void hookOnNext(Long value) {
                        simulateSlowWork(5);
                        System.out.println("processed from buffer: " + value);
                        request(1);
                    }

                    @Override
                    protected void hookOnComplete() {
                        System.out.println("stream completed, buffer capacity=" + bufferCapacity
                                + ", evicted (oldest-dropped) " + evicted.get() + " elements");
                        done.countDown();
                    }
                });
        done.await(5, TimeUnit.SECONDS);
        System.out.println("(default unbounded buffer size would have been " + Queues.SMALL_BUFFER_SIZE + " for comparison)");
    }

    private static void simulateSlowWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
