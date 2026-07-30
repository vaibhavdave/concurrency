package com.concurrency.lab.m07_producer_consumer_blocking_queues;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MultiStageQueuePipelineDemo {

    private static final String POISON_PILL = "__END__";

    public static void main(String[] args) throws Exception {
        List<String> rawLines = List.of("3,4", "10,20", "7,7", "1,100", "50,50");

        BlockingQueue<String> rawQueue = new LinkedBlockingQueue<>();
        BlockingQueue<int[]> parsedQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> sumsQueue = new LinkedBlockingQueue<>();

        Thread parseStage = new Thread(() -> {
            try {
                String line;
                while (!(line = rawQueue.take()).equals(POISON_PILL)) {
                    String[] parts = line.split(",");
                    int[] numbers = {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
                    System.out.println("[parse] '" + line + "' -> [" + numbers[0] + ", " + numbers[1] + "]");
                    parsedQueue.put(numbers);
                }
                parsedQueue.put(new int[]{Integer.MIN_VALUE});
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "parse-stage");

        Thread transformStage = new Thread(() -> {
            try {
                int[] numbers;
                while ((numbers = parsedQueue.take())[0] != Integer.MIN_VALUE) {
                    int sum = numbers[0] + numbers[1];
                    System.out.println("[transform] [" + numbers[0] + ", " + numbers[1] + "] -> sum=" + sum);
                    sumsQueue.put(sum);
                }
                sumsQueue.put(-1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "transform-stage");

        Thread sinkStage = new Thread(() -> {
            try {
                int total = 0;
                int value;
                while ((value = sumsQueue.take()) != -1) {
                    total += value;
                    System.out.println("[sink] received sum=" + value + " (running total=" + total + ")");
                }
                System.out.println("[sink] pipeline finished, grand total=" + total);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sink-stage");

        parseStage.start();
        transformStage.start();
        sinkStage.start();

        for (String line : rawLines) {
            rawQueue.put(line);
        }
        rawQueue.put(POISON_PILL);

        parseStage.join();
        transformStage.join();
        sinkStage.join();
    }
}
