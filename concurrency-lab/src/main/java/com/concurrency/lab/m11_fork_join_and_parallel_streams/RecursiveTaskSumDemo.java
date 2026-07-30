package com.concurrency.lab.m11_fork_join_and_parallel_streams;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class RecursiveTaskSumDemo {

    private static final int THRESHOLD = 10_000;

    public static void main(String[] args) {
        int size = 50_000_000;
        long[] data = new long[size];
        for (int i = 0; i < size; i++) {
            data[i] = i % 100;
        }

        long sequentialStart = System.nanoTime();
        long sequentialSum = sequentialSum(data);
        long sequentialMillis = (System.nanoTime() - sequentialStart) / 1_000_000;
        System.out.println("Sequential sum = " + sequentialSum + " in " + sequentialMillis + " ms");

        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        try {
            long parallelStart = System.nanoTime();
            long parallelSum = pool.invoke(new SumTask(data, 0, data.length));
            long parallelMillis = (System.nanoTime() - parallelStart) / 1_000_000;
            System.out.println("Parallel sum   = " + parallelSum + " in " + parallelMillis + " ms"
                    + " using " + pool.getParallelism() + " workers");

            double speedup = sequentialMillis == 0 ? 0 : (double) sequentialMillis / Math.max(1, parallelMillis);
            System.out.printf("Speedup ~= %.2fx%n", speedup);
        } finally {
            pool.shutdown();
        }
    }

    static long sequentialSum(long[] data) {
        long sum = 0;
        for (long value : data) {
            sum += value;
        }
        return sum;
    }

    static class SumTask extends RecursiveTask<Long> {
        private final long[] data;
        private final int start;
        private final int end;

        SumTask(long[] data, int start, int end) {
            this.data = data;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            int length = end - start;
            if (length <= THRESHOLD) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += data[i];
                }
                return sum;
            }

            int mid = start + length / 2;
            SumTask left = new SumTask(data, start, mid);
            SumTask right = new SumTask(data, mid, end);

            // fork one side so it can run on another worker while we compute the other inline
            left.fork();
            long rightResult = right.compute();
            long leftResult = left.join();
            return leftResult + rightResult;
        }
    }
}
