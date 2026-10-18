package com.thinklab.application.dto.response;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * A policy and how its asset is doing right now: {@code level}, {@code utilisationPercent}, {@code stale}, {@code forecast}, {@code daysToLimit}
 * and {@code projectedExhaustionAt} are judged when read, never stored.
 */
@Serdeable
public record CapacityPolicyResponse(UUID id, UUID organisationId, String name, UUID assetId, String metric, double limit, int warnPercent, int criticalPercent,
                                     int windowDays, int horizonDays, int reportEveryHours, String status, String level, @Nullable Double utilisationPercent, boolean stale,
                                     @Nullable String forecast, @Nullable Double daysToLimit, @Nullable Instant projectedExhaustionAt, @Nullable Double lastValue,
                                     @Nullable Instant lastMeasuredAt, Instant createdAt, Instant updatedAt) {}
