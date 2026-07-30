package com.concurrency.lab.m17_performance_and_observability;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;

public class ThreadDumpAnalysisDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Full thread dump ===");
        printAllThreads();

        System.out.println();
        System.out.println("=== Deliberate two-lock deadlock ===");
        DeadlockScenario scenario = new DeadlockScenario();
        scenario.start();

        long[] deadlockedIds = waitForDeadlock(Duration.ofSeconds(5));
        printDeadlockedThreads(deadlockedIds);

        scenario.shutdown();
    }

    static void printAllThreads() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo info : bean.dumpAllThreads(false, false)) {
            System.out.printf("[%d] %s - %s%n", info.getThreadId(), info.getThreadName(), info.getThreadState());
        }
    }

    /**
     * Polls {@link ThreadMXBean#findDeadlockedThreads()} until it reports a deadlock or the
     * timeout elapses. Returns {@code null} if no deadlock was detected in time.
     */
    public static long[] waitForDeadlock(Duration timeout) throws InterruptedException {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            long[] ids = bean.findDeadlockedThreads();
            if (ids != null) {
                return ids;
            }
            Thread.sleep(50);
        }
        return null;
    }

    static void printDeadlockedThreads(long[] threadIds) {
        if (threadIds == null) {
            System.out.println("No deadlock detected within the wait window.");
            return;
        }
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = bean.getThreadInfo(threadIds, true, true);
        System.out.println("Deadlock detected among " + threadIds.length + " threads:");
        for (ThreadInfo info : infos) {
            System.out.println(info);
        }
    }

    /**
     * Two daemon threads that acquire {@code lockA} then {@code lockB} (and vice versa) in
     * opposite order, guaranteeing a classic deadlock. Daemon threads plus a bounded hold time
     * before the second lock attempt keep this self-contained: the JVM can still exit even
     * though these two threads never finish.
     */
    public static final class DeadlockScenario {
        private final Object lockA = new Object();
        private final Object lockB = new Object();
        private Thread threadA;
        private Thread threadB;

        public void start() {
            threadA = new Thread(() -> {
                synchronized (lockA) {
                    sleepQuietly(200);
                    synchronized (lockB) {
                        // unreachable
                    }
                }
            }, "deadlock-thread-A");

            threadB = new Thread(() -> {
                synchronized (lockB) {
                    sleepQuietly(200);
                    synchronized (lockA) {
                        // unreachable
                    }
                }
            }, "deadlock-thread-B");

            threadA.setDaemon(true);
            threadB.setDaemon(true);
            threadA.start();
            threadB.start();
        }

        public void shutdown() {
            // the threads are permanently blocked and cannot be joined; interrupting them is a
            // best-effort cleanup signal, and daemon status keeps the JVM from hanging on exit
            threadA.interrupt();
            threadB.interrupt();
        }

        private static void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
