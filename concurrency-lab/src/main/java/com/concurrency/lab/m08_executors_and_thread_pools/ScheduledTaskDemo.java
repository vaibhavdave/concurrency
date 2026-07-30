package com.concurrency.lab.m08_executors_and_thread_pools;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledTaskDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== scheduleAtFixedRate: period measured from start-to-start ==");
        ScheduledExecutorService fixedRateScheduler = Executors.newScheduledThreadPool(1);
        AtomicInteger fixedRateRun = new AtomicInteger();
        long fixedRateStart = System.currentTimeMillis();
        fixedRateScheduler.scheduleAtFixedRate(() -> {
            int run = fixedRateRun.incrementAndGet();
            long elapsed = System.currentTimeMillis() - fixedRateStart;
            System.out.println("[fixedRate] run " + run + " starting at t=" + elapsed + "ms");
            // why fixed-rate can overlap-compensate but fixed-delay can't: once a run overruns its period,
            // the executor fires the next run immediately (back-to-back) to catch up to the schedule
            if (run == 2) {
                sleepQuietly(300);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(900);
        fixedRateScheduler.shutdownNow();

        System.out.println("== scheduleWithFixedDelay: delay measured from end-of-run to next start ==");
        ScheduledExecutorService fixedDelayScheduler = Executors.newScheduledThreadPool(1);
        AtomicInteger fixedDelayRun = new AtomicInteger();
        long fixedDelayStart = System.currentTimeMillis();
        fixedDelayScheduler.scheduleWithFixedDelay(() -> {
            int run = fixedDelayRun.incrementAndGet();
            long elapsed = System.currentTimeMillis() - fixedDelayStart;
            System.out.println("[fixedDelay] run " + run + " starting at t=" + elapsed + "ms");
            if (run == 2) {
                sleepQuietly(300);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(900);
        fixedDelayScheduler.shutdownNow();

        System.out.println("Observe: fixedRate keeps runs anchored to the original schedule (runs bunch up "
                + "after the slow task), while fixedDelay simply shifts every subsequent run later.");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
