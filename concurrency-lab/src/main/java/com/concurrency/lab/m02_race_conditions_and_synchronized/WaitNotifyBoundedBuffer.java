package com.concurrency.lab.m02_race_conditions_and_synchronized;

import java.util.ArrayDeque;
import java.util.Deque;

public class WaitNotifyBoundedBuffer<T> {

    private final Deque<T> buffer = new ArrayDeque<>();
    private final int capacity;

    public WaitNotifyBoundedBuffer(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void put(T item) throws InterruptedException {
        while (buffer.size() == capacity) {
            wait();
        }
        buffer.addLast(item);
        notifyAll();
    }

    public synchronized T take() throws InterruptedException {
        while (buffer.isEmpty()) {
            wait();
        }
        T item = buffer.removeFirst();
        notifyAll();
        return item;
    }

    public synchronized int size() {
        return buffer.size();
    }
}
