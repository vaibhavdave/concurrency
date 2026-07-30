package com.concurrency.lab.m15_reactive_webflux;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.time.Duration;

class BackpressureTest {

    @Test
    void requestingFewerElementsThanAvailableOnlyEmitsWhatWasRequested() {
        Flux<Integer> source = Flux.range(1, 10);

        StepVerifier.create(source, 0)
                .expectSubscription()
                .thenRequest(3)
                .expectNext(1, 2, 3)
                .thenRequest(7)
                .expectNext(4, 5, 6, 7, 8, 9, 10)
                .verifyComplete();
    }

    @Test
    void onBackpressureDropDropsElementsEmittedBeyondRequestedDemand() {
        // The generator runs synchronously at subscription time, pushing all 5 values
        // immediately regardless of demand; with OverflowStrategy.DROP, any value
        // pushed while requested demand is exhausted is discarded rather than queued.
        Flux<Integer> source = Flux.create(sink -> {
            for (int i = 1; i <= 5; i++) {
                sink.next(i);
            }
            sink.complete();
        }, FluxSink.OverflowStrategy.DROP);

        StepVerifier.create(source, 2)
                .expectNext(1, 2)
                .verifyComplete();
    }

    @Test
    void onBackpressureBufferRetainsElementsUntilRequested() {
        Flux<Integer> source = Flux.create(sink -> {
            for (int i = 1; i <= 5; i++) {
                sink.next(i);
            }
            sink.complete();
        }, FluxSink.OverflowStrategy.BUFFER);

        StepVerifier.create(source, 0)
                .expectSubscription()
                .thenRequest(2)
                .expectNext(1, 2)
                .thenRequest(3)
                .expectNext(3, 4, 5)
                .verifyComplete();
    }

    @Test
    void slowConsumerViaThenAwaitEventuallyReceivesAllBufferedElements() {
        Flux<Long> source = Flux.interval(Duration.ofMillis(1)).take(5);

        StepVerifier.create(source)
                .expectNextCount(5)
                .verifyComplete();
    }
}
