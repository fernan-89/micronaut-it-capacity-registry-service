package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.CapacityPolicyResponse;
import com.thinklab.application.mapper.CapacityMapper;
import com.thinklab.domain.exception.CapacityPolicyNotFoundException;
import com.thinklab.domain.repository.CapacityPolicyRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reading one policy of the tenant, judged now (BIAN Behavior Qualifier: {@code retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveCapacityPolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveCapacityPolicyUseCase.class);

    private final CapacityPolicyRepository repository;
    private final CapacityAssessor assessor;

    public RetrieveCapacityPolicyUseCase(CapacityPolicyRepository repository, CapacityAssessor assessor) {
        this.repository = repository;
        this.assessor = assessor;
    }

    public Mono<CapacityPolicyResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving CapacityPolicy ID: {}", id);

        return Mono.fromRunnable(() -> CapacityAccess.requireStaff(role, "read a capacity policy"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new CapacityPolicyNotFoundException("CapacityPolicy " + id + " not found.")))
                .flatMap(assessor::judge)
                .map(judged -> CapacityMapper.toResponse(judged.policy(), judged.assessment()));
    }
}
