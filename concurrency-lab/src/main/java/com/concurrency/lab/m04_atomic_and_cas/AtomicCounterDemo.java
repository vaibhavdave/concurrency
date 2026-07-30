package com.concurrency.lab.m04_atomic_and_cas;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AtomicCounterDemo {

    public static void main(String[] args) {
        demoAtomicIntegerBasics();
        demoManualCasLoop();
        demoGetAndUpdateAccumulateAndGet();
    }

    private static void demoAtomicIntegerBasics() {
        System.out.println("== AtomicInteger / AtomicLong basic methods ==");
        AtomicInteger counter = new AtomicInteger(0);
        System.out.println("incrementAndGet -> " + counter.incrementAndGet());
        System.out.println("getAndIncrement -> " + counter.getAndIncrement() + " (returns old value)");
        System.out.println("current value    -> " + counter.get());
        System.out.println("addAndGet(5)     -> " + counter.addAndGet(5));

        AtomicLong longCounter = new AtomicLong(100);
        System.out.println("AtomicLong decrementAndGet -> " + longCounter.decrementAndGet());
    }

    private static void demoManualCasLoop() {
        System.out.println("== Manual compareAndSet retry loop (what incrementAndGet does internally) ==");
        AtomicInteger value = new AtomicInteger(10);
        int oldValue;
        int newValue;
        do {
            oldValue = value.get();
            newValue = oldValue * 2;
            // Retry because another thread may have changed `value` between our
            // read (get()) and our write (compareAndSet()); CAS only succeeds
            // if the current value still equals oldValue.
        } while (!value.compareAndSet(oldValue, newValue));
        System.out.println("doubled 10 -> " + value.get() + " via manual CAS loop");
    }

    private static void demoGetAndUpdateAccumulateAndGet() {
        System.out.println("== getAndUpdate / accumulateAndGet ==");
        AtomicInteger value = new AtomicInteger(5);
        int old = value.getAndUpdate(v -> v + 100);
        System.out.println("getAndUpdate(+100): old=" + old + ", new=" + value.get());

        int accumulated = value.accumulateAndGet(3, (current, x) -> current * x);
        System.out.println("accumulateAndGet(*3): result=" + accumulated);
    }
}
