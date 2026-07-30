package com.concurrency.lab.m14_virtual_threads_structured_concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ManualStructuredConcurrencyDemo {

    public static void main(String[] args) {
        System.out.println("== Manual structured concurrency (happy path) ==");
        try {
            List<Integer> results = forkJoinAll(List.of(10, 20, 30), ManualStructuredConcurrencyDemo::fetchOk);
            System.out.println("All subtasks succeeded: " + results);
        } catch (Exception e) {
            System.out.println("Unexpected failure: " + e);
        }

        System.out.println("\n== Manual structured concurrency (one subtask fails) ==");
        try {
            forkJoinAll(List.of(10, 200, 30), ManualStructuredConcurrencyDemo::fetchMaybeFailing);
            System.out.println("This should not print");
        } catch (Exception e) {
            System.out.println("Scope failed as expected, first cause propagated: " + e.getCause());
        }
    }

    private static <T> List<T> forkJoinAll(List<Integer> taskDelaysMillis, java.util.function.IntFunction<T> work)
            throws InterruptedException, ExecutionException {
        // ExecutorService implements AutoCloseable (Java 19+); close() waits for
        // running tasks and, combined with explicit cancel() below, gives us the
        // "cancel siblings on first failure" behavior that StructuredTaskScope
        // provides natively via ShutdownOnFailure.
        try (ExecutorService scope = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>();
            for (int delay : taskDelaysMillis) {
                futures.add(scope.submit(() -> work.apply(delay)));
            }

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    futures.forEach(f -> f.cancel(true));
                    throw e;
                }
            }
            return results;
        }
    }

    private static int fetchOk(int delayMillis) {
        sleepQuietly(delayMillis);
        return delayMillis;
    }

    private static int fetchMaybeFailing(int delayMillis) {
        sleepQuietly(delayMillis);
        if (delayMillis >= 200) {
            throw new IllegalStateException("simulated failure for delay=" + delayMillis);
        }
        return delayMillis;
    }

    private static void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("interrupted while simulating work");
        }
    }
}
