package com.thinklab.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapacitySampleTest {

    private final UUID id = UUID.randomUUID();
    private final UUID org = UUID.randomUUID();
    private final UUID policy = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private final Instant measured = Instant.parse("2026-10-18T10:00:00.123456Z");

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    @Test
    @DisplayName("a sample is a fact: the moment is cut to the millisecond, who reported it and when are kept")
    void createNew() {
        CapacitySample sample = CapacitySample.createNew(id, org, policy, 42.5, measured, "tool", now);

        assertEquals(id, sample.getId());
        assertEquals(org, sample.getOrganisationId());
        assertEquals(policy, sample.getPolicyId());
        assertEquals(42.5, sample.getValue());
        assertEquals(Instant.parse("2026-10-18T10:00:00.123Z"), sample.getMeasuredAt());
        assertEquals("tool", sample.getReportedBy());
        assertEquals(now, sample.getReportedAt());
        assertEquals(0.0, CapacitySample.createNew(id, org, policy, 0, measured, "tool", now).getValue());
    }

    @Test
    @DisplayName("identity, a real non-negative number, the moment and the reporter are checked; a few minutes of clock skew are allowed, more is the future")
    void validation() {
        rejects(() -> CapacitySample.createNew(null, org, policy, 1, measured, "tool", now));
        rejects(() -> CapacitySample.createNew(id, null, policy, 1, measured, "tool", now));
        rejects(() -> CapacitySample.createNew(id, org, null, 1, measured, "tool", now));
        rejects(() -> CapacitySample.createNew(id, org, policy, -0.1, measured, "tool", now));
        rejects(() -> CapacitySample.createNew(id, org, policy, Double.NaN, measured, "tool", now));
        rejects(() -> CapacitySample.createNew(id, org, policy, Double.POSITIVE_INFINITY, measured, "tool", now));
        rejects(() -> CapacitySample.createNew(id, org, policy, 1, null, "tool", now));
        rejects(() -> CapacitySample.createNew(id, org, policy, 1, measured, null, now));
        rejects(() -> CapacitySample.createNew(id, org, policy, 1, measured, " ", now));
        CapacitySample skewed = CapacitySample.createNew(id, org, policy, 1, now.plus(CapacitySample.CLOCK_SKEW), "tool", now);
        assertEquals(now.plus(CapacitySample.CLOCK_SKEW), skewed.getMeasuredAt());
        rejects(() -> CapacitySample.createNew(id, org, policy, 1, now.plus(CapacitySample.CLOCK_SKEW).plus(Duration.ofMillis(1)), "tool", now));
    }

    @Test
    @DisplayName("reconstitute keeps what was stored and refuses a missing identity or moment")
    void reconstitute() {
        CapacitySample sample = CapacitySample.reconstitute(id, org, policy, 7.0, measured, "tool", now);

        assertEquals(measured, sample.getMeasuredAt());
        assertEquals(7.0, sample.getValue());
        rejects(() -> CapacitySample.reconstitute(null, org, policy, 7, measured, "tool", now));
        rejects(() -> CapacitySample.reconstitute(id, null, policy, 7, measured, "tool", now));
        rejects(() -> CapacitySample.reconstitute(id, org, null, 7, measured, "tool", now));
        rejects(() -> CapacitySample.reconstitute(id, org, policy, 7, null, "tool", now));
    }
}
