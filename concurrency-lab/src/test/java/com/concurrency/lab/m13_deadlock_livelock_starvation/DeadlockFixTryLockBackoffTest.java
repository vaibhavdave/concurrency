package com.concurrency.lab.m13_deadlock_livelock_starvation;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DeadlockFixTryLockBackoffTest {

    @Test
    void completesWithoutDeadlockWithinTimeout() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> DeadlockFixTryLockBackoffDemo.main(new String[0]));
    }
}
