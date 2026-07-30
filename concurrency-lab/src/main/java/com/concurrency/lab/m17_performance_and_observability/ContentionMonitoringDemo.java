package com.concurrency.lab.m17_performance_and_observability;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class ContentionMonitoringDemo {

    public static void main(String[] args) throws InterruptedException {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!bean.isThreadContentionMonitoringSupported()) {
            System.out.println("Thread contention monitoring is not supported on this JVM.");
            return;
        }

        Map<String, ThreadInfo> report = runContendedWorkload(6, 300, 2);

        System.out.println("=== Contention report (contention monitoring enabled) ===");
        report.forEach((name, info) -> System.out.printf(
                "%-22s blockedCount=%-6d blockedTimeMs=%-6d waitedCount=%-6d waitedTimeMs=%-6d%n",
                name, info.getBlockedCount(), info.getBlockedTime(), info.getWaitedCount(), info.getWaitedTime()));
    }

    /**
     * Runs {@code threadCount} threads through {@code iterationsPerThread} passes of a shared
     * {@code synchronized} block that briefly holds the lock, guaranteeing contention. Each
     * thread is parked on a second latch after finishing its work so a {@link ThreadInfo}
     * snapshot can still be captured while it is alive - a terminated thread's contention
     * counters are no longer queryable via {@link ThreadMXBean#getThreadInfo(long)}.
     */
    public static Map<String, ThreadInfo> runContendedWorkload(int threadCount, int iterationsPerThread,
            long holdMillis) throws InterruptedException {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        bean.setThreadContentionMonitoringEnabled(true);

        Object sharedLock = new Object();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch workDone = new CountDownLatch(threadCount);
        CountDownLatch release = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterationsPerThread; j++) {
                        synchronized (sharedLock) {
                            Thread.sleep(holdMillis);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    workDone.countDown();
                }
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "contended-worker-" + i);
            threads.add(t);
            t.start();
        }

        start.countDown();
        workDone.await();

        Map<String, ThreadInfo> report = new LinkedHashMap<>();
        for (Thread t : threads) {
            ThreadInfo info = bean.getThreadInfo(t.getId());
            if (info != null) {
                report.put(t.getName(), info);
            }
        }

        release.countDown();
        for (Thread t : threads) {
            t.join(2000);
        }
        return report;
    }
}
