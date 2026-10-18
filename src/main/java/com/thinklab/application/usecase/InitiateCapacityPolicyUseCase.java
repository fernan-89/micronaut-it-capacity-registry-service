package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateCapacityPolicyRequest;
import com.thinklab.application.dto.response.CapacityPolicyResponse;
import com.thinklab.application.mapper.CapacityMapper;
import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.CapacityPolicyRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Use Case for promising something about an asset (BIAN Behavior Qualifier: {@code initiate}). Staff only. */
@Singleton
public class InitiateCapacityPolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateCapacityPolicyUseCase.class);

    private final HashServicePort hashServicePort;
    private final CapacityPolicyRepository repository;
    private final Clock clock;

    public InitiateCapacityPolicyUseCase(HashServicePort hashServicePort, CapacityPolicyRepository repository, Clock clock) {
        this.hashServicePort = hashServicePort;
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<CapacityPolicyResponse> execute(UUID organisationId, InitiateCapacityPolicyRequest request, String executor, String role) {
        log.info("[USE CASE] Creating the CapacityPolicy [{}] for organisation: {}", request.name(), organisationId);

        return Mono.fromRunnable(() -> CapacityAccess.requireStaff(role, "create a capacity policy"))
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("capacity-policy-creation")))
                .map(id -> CapacityPolicy.createNew(id, organisationId, request.name(), request.assetId(), request.metric(), request.limit(), request.warnPercent(),
                        request.criticalPercent(), request.windowDays(), request.horizonDays(), request.reportEveryHours(), executor))
                .flatMap(repository::create)
                .map(policy -> CapacityMapper.toResponse(policy, policy.assess(Instant.now(clock), List.of())));
    }
}
