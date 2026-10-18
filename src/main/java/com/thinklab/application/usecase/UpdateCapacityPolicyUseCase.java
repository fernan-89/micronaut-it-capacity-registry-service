package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateCapacityPolicyRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for changing a policy (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateCapacityPolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateCapacityPolicyUseCase.class);

    private final CapacityPolicyWorkflow workflow;

    public UpdateCapacityPolicyUseCase(CapacityPolicyWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateCapacityPolicyRequest request, String executor, String role) {
        log.info("[USE CASE] Updating CapacityPolicy ID: {}", id);

        return workflow.apply(id, organisationId, role, "change a capacity policy",
                policy -> policy.update(request.name(), request.assetId(), request.metric(), request.limit(), request.warnPercent(), request.criticalPercent(),
                        request.windowDays(), request.horizonDays(), request.reportEveryHours(), executor));
    }
}
