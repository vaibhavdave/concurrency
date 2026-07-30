package com.concurrency.lab.m06_concurrent_collections;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

public class CopyOnWriteArrayListDemo {

    static void safeIterationWithCopyOnWriteArrayList() throws InterruptedException {
        List<Integer> list = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(i);
        }

        CountDownLatch mutatorStarted = new CountDownLatch(1);
        CountDownLatch mutatorDone = new CountDownLatch(1);
        Thread mutator = new Thread(() -> {
            mutatorStarted.countDown();
            for (int i = 5; i < 10; i++) {
                list.add(i);
            }
            mutatorDone.countDown();
        });
        mutator.start();
        mutatorStarted.await();

        int seen = 0;
        // iterator snapshots the backing array at creation time (COW semantics) -> never throws
        // ConcurrentModificationException, but it may not observe the mutator's concurrent adds
        for (Integer value : list) {
            seen++;
        }
        mutatorDone.await();
        mutator.join();

        System.out.println("Iterated over a snapshot with " + seen + " elements while another thread was adding");
        System.out.println("(final list size=" + list.size() + "); no ConcurrentModificationException was thrown.");
    }

    static void unsafeIterationWithPlainArrayList() throws InterruptedException {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(i);
        }

        CountDownLatch mutatorStarted = new CountDownLatch(1);
        Thread mutator = new Thread(() -> {
            mutatorStarted.countDown();
            try {
                for (int i = 5; i < 10_000; i++) {
                    synchronized (list) {
                        list.add(i);
                    }
                }
            } catch (RuntimeException ignored) {
                // the shared list may already be in an inconsistent state once the reader crashes
            }
        });
        mutator.start();
        mutatorStarted.await();

        try {
            int seen = 0;
            for (Integer value : list) {
                seen++;
            }
            System.out.println("Unexpectedly finished iterating " + seen + " elements without a CME (timing-dependent)");
        } catch (java.util.ConcurrentModificationException e) {
            System.out.println("Caught expected ConcurrentModificationException while iterating a plain ArrayList");
            System.out.println("that another thread was mutating: " + e.getClass().getSimpleName());
        }
        mutator.join();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. Safe iteration with CopyOnWriteArrayList while another thread mutates ==");
        safeIterationWithCopyOnWriteArrayList();

        System.out.println("== 2. Same scenario with a plain ArrayList (labeled demonstration of the failure) ==");
        unsafeIterationWithPlainArrayList();

        System.out.println("== 3. Cost tradeoff ==");
        System.out.println("CopyOnWriteArrayList copies the entire backing array on every add/remove/set, so writes");
        System.out.println("are O(n) and expensive under heavy mutation. In exchange, iteration never needs a lock");
        System.out.println("or throws CME: readers work off an immutable snapshot array. It is a good fit for");
        System.out.println("read-mostly, iteration-heavy, write-rare collections (e.g. listener lists); a poor fit");
        System.out.println("for collections with frequent writes.");
    }
}
