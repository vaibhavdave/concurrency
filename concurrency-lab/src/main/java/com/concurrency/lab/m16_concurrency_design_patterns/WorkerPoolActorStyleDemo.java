package com.concurrency.lab.m16_concurrency_design_patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Each {@link Actor} owns a private mailbox drained by exactly one dedicated worker thread.
 * Because only that one thread ever reads or writes the actor's internal state, no locks or
 * atomics are needed inside the actor itself — the mailbox is the sole synchronization point.
 */
public class WorkerPoolActorStyleDemo {

    sealed interface Message permits Increment, GetTotal, Stop {
    }

    record Increment(long amount) implements Message {
    }

    record GetTotal(CountDownLatch done, AtomicLong resultHolder) implements Message {
    }

    record Stop() implements Message {
    }

    static final class Actor {
        private final String name;
        private final BlockingQueue<Message> mailbox = new LinkedBlockingQueue<>();
        private final Thread worker;
        private long total = 0;

        Actor(String name) {
            this.name = name;
            this.worker = new Thread(this::run, "actor-" + name);
            this.worker.start();
        }

        void tell(Message message) {
            mailbox.add(message);
        }

        private void run() {
            try {
                while (true) {
                    Message message = mailbox.take();
                    if (message instanceof Increment increment) {
                        total += increment.amount();
                    } else if (message instanceof GetTotal getTotal) {
                        getTotal.resultHolder().set(total);
                        getTotal.done().countDown();
                    } else if (message instanceof Stop) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long queryTotal() throws InterruptedException {
            CountDownLatch done = new CountDownLatch(1);
            AtomicLong resultHolder = new AtomicLong();
            tell(new GetTotal(done, resultHolder));
            done.await(5, TimeUnit.SECONDS);
            return resultHolder.get();
        }

        void stop() throws InterruptedException {
            tell(new Stop());
            worker.join(2000);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        List<Actor> actors = List.of(new Actor("alice"), new Actor("bob"), new Actor("carol"));

        int producerCount = 4;
        int messagesPerProducer = 2500;
        CountDownLatch producersDone = new CountDownLatch(producerCount);
        ExecutorService producers = Executors.newFixedThreadPool(producerCount);

        for (int p = 0; p < producerCount; p++) {
            producers.submit(() -> {
                try {
                    for (int i = 0; i < messagesPerProducer; i++) {
                        Actor target = actors.get(i % actors.size());
                        target.tell(new Increment(1));
                    }
                } finally {
                    producersDone.countDown();
                }
            });
        }

        producersDone.await(10, TimeUnit.SECONDS);
        producers.shutdown();

        List<Long> totals = new ArrayList<>();
        for (Actor actor : actors) {
            long total = actor.queryTotal();
            totals.add(total);
            System.out.printf("actor-%s final total = %d%n", actor.name, total);
        }
        for (Actor actor : actors) {
            actor.stop();
        }

        long grandTotal = totals.stream().mapToLong(Long::longValue).sum();
        long expected = (long) producerCount * messagesPerProducer;
        System.out.printf("grand total = %d (expected %d) -> %s%n",
                grandTotal, expected, grandTotal == expected ? "CORRECT" : "MISMATCH");
    }
}
