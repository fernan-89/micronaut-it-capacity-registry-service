package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@code sample/initiate}: what the tool reports when it measures. The value is in the unit of the policy's limit. Reporting the same
 * policy and moment again answers the sample already stored.
 */
@Serdeable
public record ReportCapacitySampleRequest(
        @NotNull(message = "The policy is required") UUID policyId,
        @NotNull(message = "The value is required") @PositiveOrZero(message = "The value cannot be negative") Double value,
        @NotNull(message = "When the sample was taken is required") Instant measuredAt
) {}
