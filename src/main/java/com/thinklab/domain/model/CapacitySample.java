package com.thinklab.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Secondary aggregate of the IT Capacity Registry Service Domain: one measurement of a policy's metric, as the tool that took it reported it
 * (ADR-030). It is a fact, so it is immutable: never edited, never deleted by hand (old ones expire, ADR-033). Reporting the same policy and
 * moment twice is the same sample.
 */
public class CapacitySample {

    /** How far ahead of the registry's clock a sample may claim to have been taken: the tool's clock is allowed to be a little off. */
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final UUID id;
    private final UUID organisationId;
    private final UUID policyId;
    private final double value;
    private final Instant measuredAt;
    private final String reportedBy;
    private final Instant reportedAt;

    private CapacitySample(UUID id, UUID organisationId, UUID policyId, double value, Instant measuredAt, String reportedBy, Instant reportedAt) {
        this.id = id;
        this.organisationId = organisationId;
        this.policyId = policyId;
        this.value = value;
        this.measuredAt = measuredAt;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
    }

    public static CapacitySample createNew(UUID id, UUID organisationId, UUID policyId, double value, Instant measuredAt, String executor, Instant now) {
        if (id == null || organisationId == null || policyId == null) {
            throw new IllegalArgumentException("ID, Organisation ID and Policy ID are mandatory for a CapacitySample.");
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            throw new IllegalArgumentException("The value of a sample must be a number of zero or more.");
        }
        if (measuredAt == null) {
            throw new IllegalArgumentException("When the sample was taken is mandatory.");
        }
        Instant moment = truncate(measuredAt);
        if (moment.isAfter(now.plus(CLOCK_SKEW))) {
            throw new IllegalArgumentException("A sample cannot be taken in the future.");
        }
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory to report a CapacitySample.");
        }
        return new CapacitySample(id, organisationId, policyId, value, moment, executor, now);
    }

    public static CapacitySample reconstitute(UUID id, UUID organisationId, UUID policyId, double value, Instant measuredAt, String reportedBy, Instant reportedAt) {
        if (id == null || organisationId == null || policyId == null || measuredAt == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Policy ID and the moment are mandatory to reconstitute a CapacitySample.");
        }
        return new CapacitySample(id, organisationId, policyId, value, measuredAt, reportedBy, reportedAt);
    }

    /** The store keeps milliseconds, and the moment is part of the sample's identity: every time is cut to the millisecond before it is compared or looked up. */
    public static Instant truncate(Instant moment) {
        return moment.truncatedTo(ChronoUnit.MILLIS);
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getPolicyId() { return policyId; }
    public double getValue() { return value; }
    public Instant getMeasuredAt() { return measuredAt; }
    public String getReportedBy() { return reportedBy; }
    public Instant getReportedAt() { return reportedAt; }
}
