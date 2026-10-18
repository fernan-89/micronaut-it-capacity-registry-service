package com.thinklab.domain.repository;

import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.PolicyAuditEntry;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import com.thinklab.domain.model.CapacitySample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound Port for CapacityPolicy persistence. {@link #save} is a guarded write on the status loaded (ADR-033); every lookup is tenant-scoped. */
public interface CapacityPolicyRepository {

    /** Inserts the policy. A name already used in the organisation is {@link com.thinklab.domain.exception.DuplicateCapacityPolicyException}: the unique index is the atomic backstop. */
    Mono<CapacityPolicy> create(CapacityPolicy policy);

    Mono<CapacityPolicy> findById(UUID id, UUID organisationId);

    /** The tenant's policies, oldest first. */
    Flux<CapacityPolicy> findAll(UUID organisationId);

    Mono<Void> save(CapacityPolicy policy, PolicyStatus expectedStatus, PolicyAuditEntry auditEntry);

    /**
     * Folds a reported sample into the latest one the policy keeps (ADR-030). It only moves forward in time: a sample older than the one kept, or
     * the same one reported twice, changes nothing.
     */
    Mono<Void> recordSample(UUID policyId, UUID organisationId, CapacitySample sample);
}
