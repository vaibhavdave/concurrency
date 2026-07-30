package com.concurrency.lab.m13_deadlock_livelock_starvation;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DeadlockFixLockOrderingTest {

    @Test
    void completesWithoutDeadlockWithinTimeout() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> DeadlockFixLockOrderingDemo.main(new String[0]));
    }
}
