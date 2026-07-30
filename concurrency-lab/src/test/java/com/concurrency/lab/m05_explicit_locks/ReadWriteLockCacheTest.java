package com.concurrency.lab.m05_explicit_locks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ReadWriteLockCacheTest {

    @Test
    void concurrentReadersRunInParallelNotSerially() throws InterruptedException {
        ReadWriteLockDemo.Cache<String, String> cache = new ReadWriteLockDemo.Cache<>();
        cache.put("k", "v", 0);

        int readerCount = 5;
        long perReadMillis = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readerCount);

        long begin = System.nanoTime();
        for (int i = 0; i < readerCount; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    cache.get("k", perReadMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;

        long serialUpperBound = readerCount * perReadMillis;
        assertThat(elapsedMillis).isLessThan(serialUpperBound);
    }

    @Test
    void writerBlocksUntilNoReadersAndReadersBlockDuringWrite() throws InterruptedException {
        ReadWriteLockDemo.Cache<String, String> cache = new ReadWriteLockDemo.Cache<>();
        cache.put("k", "initial", 0);

        List<String> completionOrder = new CopyOnWriteArrayList<>();

        Thread writer = new Thread(() -> {
            cache.put("k", "updated", 300);
            completionOrder.add("write");
        });
        writer.start();

        // give the writer time to acquire the exclusive write lock before the reader
        // attempts readLock(); the reader must then block until the writer releases it
        Thread.sleep(50);
        Thread reader = new Thread(() -> {
            cache.get("k", 0);
            completionOrder.add("read");
        });
        reader.start();

        writer.join(5000);
        reader.join(5000);

        assertThat(completionOrder).containsExactly("write", "read");
        assertThat(cache.get("k", 0)).isEqualTo("updated");
    }

    @Test
    void putOverwritesValueVisibleToSubsequentGet() {
        ReadWriteLockDemo.Cache<String, Integer> cache = new ReadWriteLockDemo.Cache<>();
        cache.put("count", 1, 0);
        cache.put("count", 2, 0);

        await().atMost(1, TimeUnit.SECONDS).until(() -> cache.get("count", 0) == 2);
    }
}
