package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.PolicyAuditEntry;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the CapacityPolicy Aggregate for MongoDB. */
@Introspected
public class CapacityPolicyDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private String name;
    private UUID assetId;
    private String metric;
    private double limit;
    private int warnPercent;
    private int criticalPercent;
    private int windowDays;
    private int horizonDays;
    private int reportEveryHours;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Double lastValue;
    private Instant lastMeasuredAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public double getLimit() { return limit; }
    public void setLimit(double limit) { this.limit = limit; }
    public int getWarnPercent() { return warnPercent; }
    public void setWarnPercent(int warnPercent) { this.warnPercent = warnPercent; }
    public int getCriticalPercent() { return criticalPercent; }
    public void setCriticalPercent(int criticalPercent) { this.criticalPercent = criticalPercent; }
    public int getWindowDays() { return windowDays; }
    public void setWindowDays(int windowDays) { this.windowDays = windowDays; }
    public int getHorizonDays() { return horizonDays; }
    public void setHorizonDays(int horizonDays) { this.horizonDays = horizonDays; }
    public int getReportEveryHours() { return reportEveryHours; }
    public void setReportEveryHours(int reportEveryHours) { this.reportEveryHours = reportEveryHours; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Double getLastValue() { return lastValue; }
    public void setLastValue(Double lastValue) { this.lastValue = lastValue; }
    public Instant getLastMeasuredAt() { return lastMeasuredAt; }
    public void setLastMeasuredAt(Instant lastMeasuredAt) { this.lastMeasuredAt = lastMeasuredAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor, String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(PolicyAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                    entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch: fromDomain always writes entry.toStatus().name().
        PolicyAuditEntry toDomain() {
            return new PolicyAuditEntry(occurredAt, action, executor, fromStatus != null ? PolicyStatus.valueOf(fromStatus) : null, PolicyStatus.valueOf(toStatus), detail);
        }
    }

    public static final class CapacityPolicyPersistenceMapper {

        private CapacityPolicyPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static CapacityPolicyDocument toDocument(CapacityPolicy policy) {
            CapacityPolicyDocument doc = new CapacityPolicyDocument();
            doc.setId(policy.getId());
            doc.setOrganisationId(policy.getOrganisationId());
            doc.setName(policy.getName());
            doc.setAssetId(policy.getAssetId());
            doc.setMetric(policy.getMetric().name());
            doc.setLimit(policy.getLimit());
            doc.setWarnPercent(policy.getWarnPercent());
            doc.setCriticalPercent(policy.getCriticalPercent());
            doc.setWindowDays(policy.getWindowDays());
            doc.setHorizonDays(policy.getHorizonDays());
            doc.setReportEveryHours(policy.getReportEveryHours());
            doc.setStatus(policy.getStatus().name());
            doc.setCreatedAt(policy.getCreatedAt());
            doc.setUpdatedAt(policy.getUpdatedAt());
            doc.setLastValue(policy.getLastValue());
            doc.setLastMeasuredAt(policy.getLastMeasuredAt());
            doc.setAuditTrail(policy.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static CapacityPolicy toDomain(CapacityPolicyDocument doc) {
            return CapacityPolicy.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getName(), doc.getAssetId(), Metric.valueOf(doc.getMetric()), doc.getLimit(),
                    doc.getWarnPercent(), doc.getCriticalPercent(), doc.getWindowDays(), doc.getHorizonDays(), doc.getReportEveryHours(), PolicyStatus.valueOf(doc.getStatus()),
                    doc.getCreatedAt(), doc.getUpdatedAt(), doc.getLastValue(), doc.getLastMeasuredAt(),
                    doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }
    }
}
