package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record CapacitySampleResponse(UUID id, UUID organisationId, UUID policyId, double value, Instant measuredAt, String reportedBy, Instant reportedAt) {}
