package com.concurrency.lab.m03_java_memory_model_and_volatile;

public class HappensBeforeDemo {

    static class Payload {
        int a;
        int b;
        int c;

        Payload(int value) {
            a = value;
            b = value;
            c = value;
        }
    }

    private static Payload unsafeRef;
    private static volatile Payload volatileRef;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== Unsafe publication (plain reference) ==");
        runUnsafePublication();

        System.out.println("== Safe publication (volatile reference) ==");
        runSafePublication();
    }

    private static void runUnsafePublication() throws InterruptedException {
        unsafeRef = null;
        Thread writer = new Thread(() -> {
            // No happens-before edge to the reader: the reader may observe the
            // reference update before it observes fully-initialized fields.
            unsafeRef = new Payload(42);
        }, "unsafe-writer");

        Thread reader = new Thread(() -> {
            Payload local;
            while ((local = unsafeRef) == null) {
                Thread.onSpinWait();
            }
            boolean consistent = local.a == 42 && local.b == 42 && local.c == 42;
            System.out.println("reader saw Payload(a=" + local.a + ", b=" + local.b + ", c=" + local.c
                    + ") -> " + (consistent ? "fully initialized" : "TORN/PARTIAL READ (JMM allows this!)"));
        }, "unsafe-reader");

        reader.start();
        writer.start();
        writer.join();
        reader.join();
    }

    private static void runSafePublication() throws InterruptedException {
        volatileRef = null;
        Thread writer = new Thread(() -> {
            Payload payload = new Payload(42);
            // Writing to a volatile field happens-before any subsequent read of
            // that same field, so everything written before this line (the
            // Payload constructor's field writes) is guaranteed visible to
            // whichever thread performs the corresponding volatile read.
            volatileRef = payload;
        }, "safe-writer");

        Thread reader = new Thread(() -> {
            Payload local;
            while ((local = volatileRef) == null) {
                Thread.onSpinWait();
            }
            boolean consistent = local.a == 42 && local.b == 42 && local.c == 42;
            System.out.println("reader saw Payload(a=" + local.a + ", b=" + local.b + ", c=" + local.c
                    + ") -> " + (consistent ? "fully initialized (guaranteed by happens-before)" : "UNEXPECTED"));
        }, "safe-reader");

        reader.start();
        writer.start();
        writer.join();
        reader.join();
    }
}
