package com.concurrency.lab.m01_thread_fundamentals;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class ThreadCreationDemo {

    static class SubclassThread extends Thread {
        SubclassThread() {
            super("subclass-thread");
        }

        @Override
        public void run() {
            System.out.println("[" + getName() + "] running via Thread subclass");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. Thread subclass ==");
        Thread subclassThread = new SubclassThread();
        subclassThread.start();
        subclassThread.join();

        System.out.println("== 2. Runnable (anonymous class) ==");
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("[" + Thread.currentThread().getName() + "] running via Runnable");
            }
        };
        Thread runnableThread = new Thread(runnable, "runnable-thread");
        runnableThread.start();
        runnableThread.join();

        System.out.println("== 3. Runnable (lambda) ==");
        Thread lambdaThread = new Thread(
                () -> System.out.println("[" + Thread.currentThread().getName() + "] running via lambda"),
                "lambda-thread");
        lambdaThread.start();
        lambdaThread.join();

        System.out.println("== 4. Callable + FutureTask ==");
        Callable<Integer> callable = () -> {
            System.out.println("[" + Thread.currentThread().getName() + "] computing result via Callable");
            return 6 * 7;
        };
        FutureTask<Integer> futureTask = new FutureTask<>(callable);
        Thread callableThread = new Thread(futureTask, "callable-thread");
        callableThread.start();
        Integer result = futureTask.get();
        System.out.println("Callable result = " + result);

        System.out.println("All thread-creation styles completed.");
    }
}
