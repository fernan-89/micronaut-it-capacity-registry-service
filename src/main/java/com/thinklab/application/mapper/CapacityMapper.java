package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.CapacityPolicyResponse;
import com.thinklab.application.dto.response.CapacitySampleResponse;
import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.Assessment;
import com.thinklab.domain.model.CapacityPolicy.PolicyAuditEntry;
import com.thinklab.domain.model.CapacitySample;

public final class CapacityMapper {

    private CapacityMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** The policy as it stands, with how it was judged. */
    public static CapacityPolicyResponse toResponse(CapacityPolicy policy, Assessment judged) {
        return new CapacityPolicyResponse(policy.getId(), policy.getOrganisationId(), policy.getName(), policy.getAssetId(), policy.getMetric().name(), policy.getLimit(),
                policy.getWarnPercent(), policy.getCriticalPercent(), policy.getWindowDays(), policy.getHorizonDays(), policy.getReportEveryHours(), policy.getStatus().name(),
                judged.level().name(), judged.utilisationPercent(), judged.stale(), judged.forecast() != null ? judged.forecast().name() : null, judged.daysToLimit(),
                judged.projectedExhaustionAt(), policy.getLastValue(), policy.getLastMeasuredAt(), policy.getCreatedAt(), policy.getUpdatedAt());
    }

    public static CapacitySampleResponse toResponse(CapacitySample sample) {
        return new CapacitySampleResponse(sample.getId(), sample.getOrganisationId(), sample.getPolicyId(), sample.getValue(), sample.getMeasuredAt(), sample.getReportedBy(),
                sample.getReportedAt());
    }

    public static AuditEntryResponse toResponse(PolicyAuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                entry.toStatus().name(), entry.detail());
    }
}
