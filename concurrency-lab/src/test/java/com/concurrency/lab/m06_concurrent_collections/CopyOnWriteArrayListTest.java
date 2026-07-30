package com.concurrency.lab.m06_concurrent_collections;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CopyOnWriteArrayListTest {

    @Test
    void iteratingCopyOnWriteArrayListWhileAnotherThreadMutatesNeverThrows() throws InterruptedException {
        List<Integer> list = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(i);
        }

        Iterator<Integer> iterator = list.iterator();

        CountDownLatch mutatorDone = new CountDownLatch(1);
        Thread mutator = new Thread(() -> {
            for (int i = 5; i < 205; i++) {
                list.add(i);
            }
            mutatorDone.countDown();
        });
        mutator.start();
        assertThat(mutatorDone.await(5, TimeUnit.SECONDS)).isTrue();
        mutator.join();

        int seen = 0;
        while (iterator.hasNext()) {
            iterator.next();
            seen++;
        }

        // the iterator was created before the mutations, and CopyOnWriteArrayList iterators are
        // fixed to the snapshot array at creation time -- they never see later writes
        assertThat(seen).isEqualTo(5);
        assertThat(list).hasSize(205);
    }

    @Test
    void iteratingPlainArrayListWhileAnotherThreadMutatesCanThrowConcurrentModificationException() throws InterruptedException {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(i);
        }

        AtomicBoolean keepMutating = new AtomicBoolean(true);
        CountDownLatch mutatorStarted = new CountDownLatch(1);
        Thread mutator = new Thread(() -> {
            mutatorStarted.countDown();
            int next = 5;
            while (keepMutating.get()) {
                synchronized (list) {
                    list.add(next++);
                }
            }
        });
        mutator.setDaemon(true);
        mutator.start();
        assertThat(mutatorStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // the mutator keeps adding elements throughout, so a plain (unsynchronized) iteration is
        // virtually certain to observe a modCount change within a handful of attempts; the point of
        // this test is that a CME CAN and DOES happen here, unlike with CopyOnWriteArrayList above
        boolean caughtConcurrentModification = false;
        for (int attempt = 0; attempt < 100 && !caughtConcurrentModification; attempt++) {
            try {
                for (Integer value : list) {
                    Thread.onSpinWait();
                }
            } catch (ConcurrentModificationException expected) {
                caughtConcurrentModification = true;
            }
        }

        keepMutating.set(false);
        mutator.join(5000);

        assertThat(caughtConcurrentModification)
                .as("expected at least one iteration over the plain ArrayList to throw ConcurrentModificationException")
                .isTrue();
    }
}
