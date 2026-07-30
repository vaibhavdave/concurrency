package com.concurrency.lab.m02_race_conditions_and_synchronized;

public class UnsafeCounter {

    private int count;

    public void increment() {
        count++;
    }

    public int get() {
        return count;
    }
}
