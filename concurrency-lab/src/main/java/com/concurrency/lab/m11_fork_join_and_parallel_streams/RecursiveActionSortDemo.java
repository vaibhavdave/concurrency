package com.concurrency.lab.m11_fork_join_and_parallel_streams;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class RecursiveActionSortDemo {

    private static final int THRESHOLD = 5_000;

    public static void main(String[] args) {
        int size = 5_000_000;
        int[] original = new int[size];
        Random random = new Random(42);
        for (int i = 0; i < size; i++) {
            original[i] = random.nextInt();
        }

        int[] sequentialCopy = Arrays.copyOf(original, original.length);
        long sequentialStart = System.nanoTime();
        Arrays.sort(sequentialCopy);
        long sequentialMillis = (System.nanoTime() - sequentialStart) / 1_000_000;
        System.out.println("Arrays.sort (sequential) took " + sequentialMillis + " ms");

        int[] parallelCopy = Arrays.copyOf(original, original.length);
        int[] buffer = new int[original.length];
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        try {
            long parallelStart = System.nanoTime();
            pool.invoke(new MergeSortTask(parallelCopy, buffer, 0, parallelCopy.length));
            long parallelMillis = (System.nanoTime() - parallelStart) / 1_000_000;
            System.out.println("Parallel merge sort took " + parallelMillis + " ms");

            boolean sortedCorrectly = Arrays.equals(sequentialCopy, parallelCopy);
            System.out.println("Result matches Arrays.sort: " + sortedCorrectly);
        } finally {
            pool.shutdown();
        }
    }

    static class MergeSortTask extends RecursiveAction {
        private final int[] array;
        private final int[] buffer;
        private final int start;
        private final int end;

        MergeSortTask(int[] array, int[] buffer, int start, int end) {
            this.array = array;
            this.buffer = buffer;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            int length = end - start;
            if (length <= THRESHOLD) {
                Arrays.sort(array, start, end);
                return;
            }

            int mid = start + length / 2;
            MergeSortTask left = new MergeSortTask(array, buffer, start, mid);
            MergeSortTask right = new MergeSortTask(array, buffer, mid, end);
            invokeAll(left, right);
            merge(mid);
        }

        private void merge(int mid) {
            int i = start;
            int j = mid;
            int k = start;
            while (i < mid && j < end) {
                buffer[k++] = array[i] <= array[j] ? array[i++] : array[j++];
            }
            while (i < mid) {
                buffer[k++] = array[i++];
            }
            while (j < end) {
                buffer[k++] = array[j++];
            }
            System.arraycopy(buffer, start, array, start, end - start);
        }
    }
}
