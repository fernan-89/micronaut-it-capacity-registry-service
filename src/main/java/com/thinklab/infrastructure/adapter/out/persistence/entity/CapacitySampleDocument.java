package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.CapacitySample;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.UUID;

/** Infrastructure-specific representation of the CapacitySample Aggregate for MongoDB. Immutable facts: only ever inserted, and expired by a TTL index on {@code measuredAt}. */
@Introspected
public class CapacitySampleDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private UUID policyId;
    private double value;
    private Instant measuredAt;
    private String reportedBy;
    private Instant reportedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public Instant getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(Instant measuredAt) { this.measuredAt = measuredAt; }
    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }
    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }

    public static final class CapacitySamplePersistenceMapper {

        private CapacitySamplePersistenceMapper() { throw new UnsupportedOperationException(); }

        public static CapacitySampleDocument toDocument(CapacitySample sample) {
            CapacitySampleDocument doc = new CapacitySampleDocument();
            doc.setId(sample.getId());
            doc.setOrganisationId(sample.getOrganisationId());
            doc.setPolicyId(sample.getPolicyId());
            doc.setValue(sample.getValue());
            doc.setMeasuredAt(sample.getMeasuredAt());
            doc.setReportedBy(sample.getReportedBy());
            doc.setReportedAt(sample.getReportedAt());
            return doc;
        }

        public static CapacitySample toDomain(CapacitySampleDocument doc) {
            return CapacitySample.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getPolicyId(), doc.getValue(), doc.getMeasuredAt(), doc.getReportedBy(), doc.getReportedAt());
        }
    }
}
