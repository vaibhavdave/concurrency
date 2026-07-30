package com.concurrency.lab.m04_atomic_and_cas;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicStampedReference;

import static org.assertj.core.api.Assertions.assertThat;

class AbaProblemTest {

    @Test
    void stampedReferenceRejectsCasWhenReferenceReturnsToSameValueButStampChanged() {
        String initial = "A";
        AtomicStampedReference<String> ref = new AtomicStampedReference<>(initial, 0);

        int capturedStamp = ref.getStamp();
        String capturedReference = ref.getReference();

        boolean intermediateChange = ref.compareAndSet(initial, "B", 0, 1);
        assertThat(intermediateChange).isTrue();

        boolean backToOriginalValue = ref.compareAndSet("B", initial, 1, 2);
        assertThat(backToOriginalValue).isTrue();
        assertThat(ref.getReference()).isEqualTo(capturedReference);
        assertThat(ref.getReference()).isSameAs(initial);

        boolean staleCasSucceeds = ref.compareAndSet(capturedReference, "C", capturedStamp, capturedStamp + 1);
        assertThat(staleCasSucceeds)
                .as("CAS using the stale stamp must be rejected even though the reference looks unchanged")
                .isFalse();

        boolean freshCasSucceeds = ref.compareAndSet(capturedReference, "C", ref.getStamp(), ref.getStamp() + 1);
        assertThat(freshCasSucceeds).isTrue();
        assertThat(ref.getReference()).isEqualTo("C");
    }

    @Test
    void plainAtomicReferenceCannotDetectTheSameAbaRoundTrip() {
        java.util.concurrent.atomic.AtomicReference<String> ref =
                new java.util.concurrent.atomic.AtomicReference<>("A");

        String capturedReference = ref.get();

        ref.set("B");
        ref.set(capturedReference);

        boolean staleCasSucceeds = ref.compareAndSet(capturedReference, "C");
        assertThat(staleCasSucceeds)
                .as("plain AtomicReference cannot tell the value went A -> B -> A; CAS spuriously succeeds")
                .isTrue();
    }
}
