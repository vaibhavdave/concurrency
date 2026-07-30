package com.concurrency.lab.m17_performance_and_observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ContentionMonitoringTest {

    @Test
    void blockedCountAndTimeAreNonZeroForThreadsThatWaitedOnAContendedLock() throws InterruptedException {
        assumeTrue(ManagementFactory.getThreadMXBean().isThreadContentionMonitoringSupported(),
                "thread contention monitoring not supported on this JVM");

        Map<String, ThreadInfo> report = ContentionMonitoringDemo.runContendedWorkload(6, 200, 3);

        assertThat(report).isNotEmpty();
        boolean anyThreadWasBlocked = report.values().stream()
                .anyMatch(info -> info.getBlockedCount() > 0 && info.getBlockedTime() >= 0);
        assertThat(anyThreadWasBlocked)
                .as("at least one of %d contended threads should have recorded a block", report.size())
                .isTrue();

        long totalBlockedCount = report.values().stream().mapToLong(ThreadInfo::getBlockedCount).sum();
        assertThat(totalBlockedCount).isGreaterThan(0);
    }
}
