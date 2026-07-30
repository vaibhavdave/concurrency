package com.concurrency.lab.m11_fork_join_and_parallel_streams;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.ForkJoinPool;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveTaskSumTest {

    @Test
    void parallelSumMatchesSequentialSumForLargeRandomArray() {
        int size = 1_000_000;
        long[] data = new long[size];
        Random random = new Random(7);
        for (int i = 0; i < size; i++) {
            data[i] = random.nextInt(1000);
        }

        long expected = RecursiveTaskSumDemo.sequentialSum(data);

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            long actual = pool.invoke(new RecursiveTaskSumDemo.SumTask(data, 0, data.length));
            assertThat(actual).isEqualTo(expected);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void parallelSumHandlesEmptyArray() {
        long[] data = new long[0];
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            long actual = pool.invoke(new RecursiveTaskSumDemo.SumTask(data, 0, data.length));
            assertThat(actual).isZero();
        } finally {
            pool.shutdown();
        }
    }
}
