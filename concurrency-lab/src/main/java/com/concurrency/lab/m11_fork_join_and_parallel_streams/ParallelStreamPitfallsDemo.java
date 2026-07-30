package com.concurrency.lab.m11_fork_join_and_parallel_streams;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParallelStreamPitfallsDemo {

    private static final int ELEMENT_COUNT = 200_000;

    public static void main(String[] args) {
        System.out.println("== Unsafe: mutating a shared ArrayList from a parallel stream ==");
        for (int attempt = 1; attempt <= 3; attempt++) {
            List<Integer> unsafeList = new ArrayList<>();
            try {
                IntStream.range(0, ELEMENT_COUNT).parallel()
                        .forEach(unsafeList::add);
            } catch (Exception e) {
                System.out.println("attempt " + attempt + ": threw " + e.getClass().getSimpleName());
                continue;
            }
            System.out.println("attempt " + attempt + ": expected size " + ELEMENT_COUNT
                    + ", actual size " + unsafeList.size()
                    + (unsafeList.size() != ELEMENT_COUNT ? "  <-- lost updates / corruption" : ""));
        }

        System.out.println();
        System.out.println("== Unsafe: racy shared counter via int++ ==");
        int[] unsafeCounter = {0};
        IntStream.range(0, ELEMENT_COUNT).parallel()
                .forEach(i -> unsafeCounter[0]++);
        System.out.println("expected " + ELEMENT_COUNT + ", actual " + unsafeCounter[0]
                + (unsafeCounter[0] != ELEMENT_COUNT ? "  <-- lost updates" : ""));

        System.out.println();
        System.out.println("== Safe fix #1: Collectors.toList() ==");
        List<Integer> safeList = IntStream.range(0, ELEMENT_COUNT).parallel()
                .boxed()
                .collect(Collectors.toList());
        System.out.println("size = " + safeList.size() + " (deterministic)");

        System.out.println();
        System.out.println("== Safe fix #2: IntStream.sum() ==");
        int sum = IntStream.range(0, ELEMENT_COUNT).parallel().sum();
        System.out.println("sum = " + sum + " (deterministic)");

        System.out.println();
        System.out.println("== Safe fix #3: AtomicLong accumulator ==");
        AtomicLong safeCounter = new AtomicLong();
        IntStream.range(0, ELEMENT_COUNT).parallel()
                .forEach(i -> safeCounter.incrementAndGet());
        System.out.println("count = " + safeCounter.get() + " (deterministic)");
    }
}
