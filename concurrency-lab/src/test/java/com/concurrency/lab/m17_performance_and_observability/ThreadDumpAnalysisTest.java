package com.concurrency.lab.m17_performance_and_observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ThreadDumpAnalysisTest {

    @Test
    void findDeadlockedThreadsDetectsTheDeliberateTwoLockDeadlockWithinTimeout() throws InterruptedException {
        ThreadDumpAnalysisDemo.DeadlockScenario scenario = new ThreadDumpAnalysisDemo.DeadlockScenario();
        scenario.start();
        try {
            long[] deadlockedIds = ThreadDumpAnalysisDemo.waitForDeadlock(Duration.ofSeconds(5));

            assertThat(deadlockedIds).isNotNull();
            assertThat(deadlockedIds).hasSize(2);
        } finally {
            // the two threads remain permanently blocked on intrinsic locks (synchronized cannot
            // be interrupted out of), so shutdown() only asks them to stop; the JVM can still
            // exit because both are daemon threads
            scenario.shutdown();
        }
    }
}
