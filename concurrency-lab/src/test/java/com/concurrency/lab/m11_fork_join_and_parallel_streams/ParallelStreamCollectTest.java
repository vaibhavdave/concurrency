package com.concurrency.lab.m11_fork_join_and_parallel_streams;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelStreamCollectTest {

    private static final int ELEMENT_COUNT = 50_000;

    @Test
    void collectToListProducesExactExpectedSizeAndContentEveryTime() {
        for (int attempt = 0; attempt < 5; attempt++) {
            List<Integer> result = IntStream.range(0, ELEMENT_COUNT).parallel()
                    .boxed()
                    .collect(Collectors.toList());

            assertThat(result).hasSize(ELEMENT_COUNT);
            assertThat(result).containsExactlyElementsOf(
                    IntStream.range(0, ELEMENT_COUNT).boxed().collect(Collectors.toList()));
        }
    }

    @Test
    void intStreamSumIsDeterministic() {
        int expected = IntStream.range(0, ELEMENT_COUNT).sum();
        int actual = IntStream.range(0, ELEMENT_COUNT).parallel().sum();
        assertThat(actual).isEqualTo(expected);
    }
}
