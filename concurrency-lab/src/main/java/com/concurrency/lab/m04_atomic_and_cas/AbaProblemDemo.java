package com.concurrency.lab.m04_atomic_and_cas;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

public class AbaProblemDemo {

    static class Node<T> {
        final T value;
        Node<T> next;

        Node(T value, Node<T> next) {
            this.value = value;
            this.next = next;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        demoAbaWithPlainAtomicReference();
        demoFixedWithAtomicStampedReference();
    }

    /**
     * Builds a stack A -> B -> C (top = A) and has one thread start popping A
     * (reading A and its "next" B) but pause before the compareAndSet. Meanwhile
     * a second thread pops A and B for real, then pushes the SAME Node A object
     * back on top (as a node pool / freelist would, to avoid allocation). The
     * first thread's CAS then sees top == A again (same reference identity) and
     * "succeeds", setting top back to B - a node that was already removed and
     * whose own `next` pointer is now stale. This is the ABA problem: the
     * reference looks unchanged (A -> A) but the structure underneath moved.
     */
    private static void demoAbaWithPlainAtomicReference() throws InterruptedException {
        System.out.println("== ABA problem with plain AtomicReference ==");

        Node<String> c = new Node<>("C", null);
        Node<String> b = new Node<>("B", c);
        Node<String> a = new Node<>("A", b);
        AtomicReference<Node<String>> top = new AtomicReference<>(a);

        CountDownLatch readerHasSnapshot = new CountDownLatch(1);
        CountDownLatch otherThreadDoneMutating = new CountDownLatch(1);

        Thread popper = new Thread(() -> {
            Node<String> oldTop = top.get();
            Node<String> expectedNewTop = oldTop.next;
            readerHasSnapshot.countDown();
            try {
                otherThreadDoneMutating.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            boolean success = top.compareAndSet(oldTop, expectedNewTop);
            System.out.println("popper CAS(top: A -> B) succeeded=" + success
                    + " even though B was already removed by another thread - stack is now corrupted");
        }, "aba-popper");

        popper.start();
        readerHasSnapshot.await();

        Node<String> poppedA = top.get();
        Node<String> poppedB = poppedA.next;
        top.set(poppedB.next);
        System.out.println("other thread popped A then B for real, top is now C");

        poppedA.next = top.get();
        top.set(poppedA);
        System.out.println("other thread re-pushed the SAME 'A' node object (e.g. from a node pool); "
                + "top is A again, reference-equal to what popper first saw");

        otherThreadDoneMutating.countDown();
        popper.join();

        System.out.println("Final top value = " + top.get().value
                + " (should logically be 'A' or 'C', but the stale B pointer corrupted the stack)");
    }

    /**
     * Same reuse-the-same-object scenario, but the reference now carries an
     * integer stamp that the popper captures alongside the reference. Even
     * though the reference goes A -> (something else) -> A, the stamp keeps
     * incrementing on every mutation, so the popper's compareAndSet (which
     * checks BOTH reference and stamp) fails and correctly retries instead of
     * corrupting the structure.
     */
    private static void demoFixedWithAtomicStampedReference() throws InterruptedException {
        System.out.println("== Fixed with AtomicStampedReference ==");

        Node<String> c = new Node<>("C", null);
        Node<String> b = new Node<>("B", c);
        Node<String> a = new Node<>("A", b);
        AtomicStampedReference<Node<String>> top = new AtomicStampedReference<>(a, 0);

        CountDownLatch readerHasSnapshot = new CountDownLatch(1);
        CountDownLatch otherThreadDoneMutating = new CountDownLatch(1);
        int[] stampHolder = new int[1];

        Thread popper = new Thread(() -> {
            Node<String> oldTop = top.getReference();
            int oldStamp = top.getStamp();
            Node<String> expectedNewTop = oldTop.next;
            stampHolder[0] = oldStamp;
            readerHasSnapshot.countDown();
            try {
                otherThreadDoneMutating.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            boolean success = top.compareAndSet(oldTop, expectedNewTop, oldStamp, oldStamp + 1);
            System.out.println("popper CAS(top: A -> B, stamp check) succeeded=" + success
                    + " (correctly rejected because the stamp changed while the reference was reused)");
        }, "safe-popper");

        popper.start();
        readerHasSnapshot.await();

        Node<String> poppedA = top.getReference();
        Node<String> poppedB = poppedA.next;
        int stamp = top.getStamp();
        top.set(poppedB.next, stamp + 1);
        stamp++;

        poppedA.next = top.getReference();
        top.set(poppedA, stamp + 1);
        System.out.println("other thread re-pushed the SAME 'A' node object again; reference is A again "
                + "but the stamp has advanced from " + stampHolder[0] + " to " + top.getStamp());

        otherThreadDoneMutating.countDown();
        popper.join();

        System.out.println("Final top value = " + top.getReference().value
                + " (correct: popper's stale CAS was rejected, no corruption)");
    }
}
