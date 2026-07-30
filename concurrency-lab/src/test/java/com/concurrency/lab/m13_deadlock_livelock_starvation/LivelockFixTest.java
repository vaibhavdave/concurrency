package com.concurrency.lab.m13_deadlock_livelock_starvation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LivelockFixTest {

    @Test
    void jitteredBackoffVersionMakesProgressWithinBound() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured));
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> LivelockDemo.main(new String[0]));
        } finally {
            System.setOut(originalOut);
        }

        String output = captured.toString();
        assertThat(output).contains("progress made = true");
    }
}
