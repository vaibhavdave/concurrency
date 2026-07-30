package com.concurrency.lab.m01_thread_fundamentals;

public class ContextSwitchCostDemo {

    private static final long TOTAL_INCREMENTS = 200_000_000L;

    public static void main(String[] args) throws InterruptedException {
        runSingleThreaded();
        runMultiThreaded(2);
        runMultiThreaded(4);
        runMultiThreaded(8);
        runMultiThreaded(50);
    }

    private static void runSingleThreaded() throws InterruptedException {
        long start = System.nanoTime();
        long[] counter = new long[1];
        for (long i = 0; i < TOTAL_INCREMENTS; i++) {
            counter[0]++;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("1 thread   : %d increments in %d ms (counter=%d)%n",
                TOTAL_INCREMENTS, elapsedMs, counter[0]);
    }

    private static void runMultiThreaded(int threadCount) throws InterruptedException {
        long perThread = TOTAL_INCREMENTS / threadCount;
        Thread[] threads = new Thread[threadCount];
        long start = System.nanoTime();
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                long local = 0;
                for (long i = 0; i < perThread; i++) {
                    local++;
                }
            });
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("%d threads : %d increments in %d ms (more threads than cores adds "
                        + "scheduling/context-switch overhead despite doing the same total work)%n",
                threadCount, perThread * threadCount, elapsedMs);
    }
}
