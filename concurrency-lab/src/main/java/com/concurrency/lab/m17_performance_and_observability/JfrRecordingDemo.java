package com.concurrency.lab.m17_performance_and_observability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import jdk.jfr.Recording;

public class JfrRecordingDemo {

    public static void main(String[] args) throws Exception {
        Path outputDir = Files.createTempDirectory("jfr-demo");
        Path recordingFile = outputDir.resolve("contention-recording.jfr");

        Recording recording = new Recording();
        recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ZERO);
        recording.enable("jdk.JavaMonitorWait").withThreshold(Duration.ZERO);
        recording.setDestination(recordingFile);

        recording.start();
        runContendedWorkload();
        recording.stop();
        recording.close();

        System.out.println("JFR recording written to: " + recordingFile.toAbsolutePath());
        System.out.println("Inspect it with:  jfr print --events jdk.JavaMonitorEnter " + recordingFile);
        System.out.println("Or open it in JDK Mission Control: File > Open File... -> select the .jfr file");
    }

    private static void runContendedWorkload() throws InterruptedException {
        Object sharedLock = new Object();
        int threadCount = 6;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 200; j++) {
                        synchronized (sharedLock) {
                            Thread.sleep(2);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "jfr-contended-worker-" + i);
            threads.add(t);
            t.start();
        }

        start.countDown();
        done.await();
        for (Thread t : threads) {
            t.join(2000);
        }
    }
}
