package com.concurrency.lab.m15_reactive_webflux;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

class MonoFluxBasicsTest {

    @Test
    void monoJustEmitsSingleValueThenCompletes() {
        StepVerifier.create(Mono.just("hello"))
                .expectNext("hello")
                .verifyComplete();
    }

    @Test
    void monoEmptyCompletesWithoutEmitting() {
        StepVerifier.create(Mono.empty())
                .verifyComplete();
    }

    @Test
    void fluxRangeEmitsExpectedSequence() {
        StepVerifier.create(Flux.range(1, 5))
                .expectNext(1, 2, 3, 4, 5)
                .verifyComplete();
    }

    @Test
    void mapAndFilterTransformSequence() {
        Flux<Integer> evenSquares = Flux.range(1, 10)
                .filter(n -> n % 2 == 0)
                .map(n -> n * n);

        StepVerifier.create(evenSquares)
                .expectNext(4, 16, 36, 64, 100)
                .verifyComplete();
    }

    @Test
    void flatMapTransformsEachElementIndependently() {
        Flux<String> upper = Flux.just("a", "b", "c")
                .flatMap(s -> Mono.just(s.toUpperCase()));

        StepVerifier.create(upper)
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void fluxPropagatesErrorSignal() {
        Flux<Integer> failing = Flux.just(1, 2, 3)
                .map(n -> {
                    if (n == 2) {
                        throw new IllegalStateException("boom");
                    }
                    return n;
                });

        StepVerifier.create(failing)
                .expectNext(1)
                .expectErrorMatches(t -> t instanceof IllegalStateException && "boom".equals(t.getMessage()))
                .verify(Duration.ofSeconds(2));
    }
}
