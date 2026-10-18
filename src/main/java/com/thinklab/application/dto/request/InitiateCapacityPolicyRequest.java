package com.thinklab.application.dto.request;

import com.thinklab.domain.model.CapacityPolicy.Metric;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO for {@code initiate}: how much of one metric an asset may use and when to worry. The critical utilisation must be above the warning one (the
 * domain refuses it with a 400 that says why). The limit is in whatever unit the tool reports in.
 */
@Serdeable
public record InitiateCapacityPolicyRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        String name,
        @NotNull(message = "The asset the policy watches is required") UUID assetId,
        @NotNull(message = "The metric is required") Metric metric,
        @NotNull(message = "The limit is required") @Positive(message = "The limit must be more than 0") @DecimalMax(value = "1.0E15", message = "The limit is too large")
        Double limit,
        @NotNull(message = "The warning utilisation is required") @Min(value = 1, message = "The warning utilisation must be at least 1 percent") @Max(value = 99, message = "The warning utilisation must not exceed 99 percent")
        Integer warnPercent,
        @NotNull(message = "The critical utilisation is required") @Min(value = 2, message = "The critical utilisation must be at least 2 percent") @Max(value = 100, message = "The critical utilisation must not exceed 100 percent")
        Integer criticalPercent,
        @NotNull(message = "The trend window is required") @Min(value = 1, message = "The trend window must be at least 1 day") @Max(value = 90, message = "The trend window must not exceed 90 days")
        Integer windowDays,
        @NotNull(message = "The forecast horizon is required") @Min(value = 1, message = "The forecast horizon must be at least 1 day") @Max(value = 365, message = "The forecast horizon must not exceed 365 days")
        Integer horizonDays,
        @NotNull(message = "The reporting interval is required") @Min(value = 1, message = "The reporting interval must be at least 1 hour") @Max(value = 720, message = "The reporting interval must not exceed 720 hours")
        Integer reportEveryHours
) {}
