package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.CapacityMapper;
import com.thinklab.domain.exception.CapacityPolicyNotFoundException;
import com.thinklab.domain.repository.CapacityPolicyRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the forensic ledger of a policy (BIAN Behavior Qualifier: {@code audit-log/retrieve}). Staff only. */
@Singleton
public class RetrieveCapacityPolicyAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveCapacityPolicyAuditLogUseCase.class);

    private final CapacityPolicyRepository repository;

    public RetrieveCapacityPolicyAuditLogUseCase(CapacityPolicyRepository repository) {
        this.repository = repository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of CapacityPolicy ID: {}", id);

        return Mono.fromRunnable(() -> CapacityAccess.requireStaff(role, "read the audit trail of a capacity policy"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new CapacityPolicyNotFoundException("CapacityPolicy " + id + " not found.")))
                .map(policy -> policy.getAuditTrail().stream().map(CapacityMapper::toResponse).collect(Collectors.toList()));
    }
}
