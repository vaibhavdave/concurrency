package com.concurrency.lab.m02_race_conditions_and_synchronized;

public class SafeSynchronizedCounter {

    private int count;

    public synchronized void increment() {
        count++;
    }

    public synchronized int get() {
        return count;
    }
}
