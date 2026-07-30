package com.concurrency.lab.m16_concurrency_design_patterns;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadSafeLazySingletonDemo {

    static final class DoubleCheckedLockingSingleton {
        private static volatile DoubleCheckedLockingSingleton instance;

        private DoubleCheckedLockingSingleton() {
        }

        static DoubleCheckedLockingSingleton getInstance() {
            DoubleCheckedLockingSingleton result = instance;
            if (result == null) {
                synchronized (DoubleCheckedLockingSingleton.class) {
                    result = instance;
                    // re-check after acquiring the lock: another thread may have already finished
                    // constructing and publishing the instance while we were waiting for the monitor
                    if (result == null) {
                        instance = result = new DoubleCheckedLockingSingleton();
                    }
                }
            }
            return result;
        }
    }

    static final class HolderIdiomSingleton {
        private HolderIdiomSingleton() {
        }

        // the JVM guarantees a class is initialized at most once, lazily, on first active use -
        // so Holder.INSTANCE is created exactly once with no explicit synchronization needed
        private static final class Holder {
            static final HolderIdiomSingleton INSTANCE = new HolderIdiomSingleton();
        }

        static HolderIdiomSingleton getInstance() {
            return Holder.INSTANCE;
        }
    }

    enum EnumSingleton {
        INSTANCE;

        int ping() {
            return 42;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 32;

        Set<DoubleCheckedLockingSingleton> dclInstances = raceToConstruct(threadCount,
                DoubleCheckedLockingSingleton::getInstance);
        System.out.println("Double-checked locking: distinct instances observed = " + dclInstances.size());

        Set<HolderIdiomSingleton> holderInstances = raceToConstruct(threadCount, HolderIdiomSingleton::getInstance);
        System.out.println("Holder idiom: distinct instances observed = " + holderInstances.size());

        Set<EnumSingleton> enumInstances = raceToConstruct(threadCount, () -> EnumSingleton.INSTANCE);
        System.out.println("Enum singleton: distinct instances observed = " + enumInstances.size());
    }

    private static <T> Set<T> raceToConstruct(int threadCount, java.util.function.Supplier<T> accessor)
            throws InterruptedException {
        CountDownLatch startingGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threadCount);
        Set<T> observed = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startingGun.await();
                    observed.add(accessor.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startingGun.countDown();
        finished.await(10, TimeUnit.SECONDS);
        pool.shutdown();
        return observed;
    }
}
