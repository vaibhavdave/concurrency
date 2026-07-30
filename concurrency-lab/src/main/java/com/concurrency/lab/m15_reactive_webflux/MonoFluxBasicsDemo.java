package com.concurrency.lab.m15_reactive_webflux;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public class MonoFluxBasicsDemo {

    public static void main(String[] args) {
        System.out.println("== Mono basics ==");
        Mono<String> mono = Mono.just("hello");
        // .block() is only used here for println demonstration; a real reactive
        // pipeline never blocks — it subscribes and reacts to signals instead.
        System.out.println("Mono.block() = " + mono.block());

        System.out.println("\n== Flux basics ==");
        Flux<Integer> flux = Flux.range(1, 5);
        List<Integer> collected = flux.collectList().block();
        System.out.println("Flux.range(1,5).collectList().block() = " + collected);

        System.out.println("\n== map / filter ==");
        Flux.range(1, 10)
                .filter(n -> n % 2 == 0)
                .map(n -> n * n)
                .toIterable()
                .forEach(n -> System.out.println("even-squared: " + n));

        System.out.println("\n== flatMap (order not guaranteed to be preserved) ==");
        Flux.just("a", "b", "c")
                .flatMap(s -> Mono.just(s.toUpperCase()))
                .toIterable()
                .forEach(s -> System.out.println("flatMapped: " + s));

        System.out.println("\n== Mono.empty() / Mono.justOrEmpty(null) ==");
        System.out.println("empty().block() = " + Mono.empty().block());
        System.out.println("justOrEmpty(null).block() = " + Mono.justOrEmpty(null).block());

        System.out.println("\nNote: .block()/.toIterable() are used above purely to print results from a");
        System.out.println("main() method. In real reactive applications you subscribe() or let a framework");
        System.out.println("(WebFlux, R2DBC) drive the pipeline — blocking defeats the purpose of reactive I/O.");
    }
}
