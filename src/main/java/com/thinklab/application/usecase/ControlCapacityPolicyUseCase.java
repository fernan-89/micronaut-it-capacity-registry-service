package com.thinklab.application.usecase;

import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.PolicyAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for pausing and resuming a policy (BIAN Behavior Qualifier: {@code control/*}). */
@Singleton
public class ControlCapacityPolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlCapacityPolicyUseCase.class);

    /** What a person can ask for; each constant says how the aggregate performs it. */
    public enum Action {
        PAUSE {
            @Override PolicyAuditEntry apply(CapacityPolicy policy, String executor) { return policy.pause(executor); }
        },
        RESUME {
            @Override PolicyAuditEntry apply(CapacityPolicy policy, String executor) { return policy.resume(executor); }
        };

        abstract PolicyAuditEntry apply(CapacityPolicy policy, String executor);
    }

    private final CapacityPolicyWorkflow workflow;

    public ControlCapacityPolicyUseCase(CapacityPolicyWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on CapacityPolicy ID: {}", action, id);

        return workflow.apply(id, organisationId, role, "pause or resume a capacity policy", policy -> action.apply(policy, executor));
    }
}
