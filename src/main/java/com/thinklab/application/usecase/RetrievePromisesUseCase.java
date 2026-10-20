package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.Promise;
import com.thinklab.domain.repository.CapacityPolicyRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use Case for the promises of the tenant's policies, judged now (BIAN Behavior Qualifier: {@code promises/retrieve}, platform ADR-001): a policy at the
 * critical level is broken ({@code CAPACITY_CRITICAL}), one at OK or WARNING is kept, one paused or without a sample is not judged. Staff only.
 */
@Singleton
public class RetrievePromisesUseCase {

    static final int JUDGED_AT_ONCE = RetrieveCapacityPoliciesUseCase.JUDGED_AT_ONCE;
    private static final Logger log = LoggerFactory.getLogger(RetrievePromisesUseCase.class);

    private final CapacityPolicyRepository repository;
    private final CapacityAssessor assessor;
    private final Clock clock;

    public RetrievePromisesUseCase(CapacityPolicyRepository repository, CapacityAssessor assessor, Clock clock) {
        this.repository = repository;
        this.assessor = assessor;
        this.clock = clock;
    }

    public Mono<List<PromiseResponse>> execute(UUID organisationId, Integer changedWithinDays, Integer limit, String role) {
        log.info("[USE CASE] Retrieving the promises of the CapacityPolicies of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> CapacityAccess.requireStaff(role, "list the promises of capacity policies"))
                .then(Mono.fromCallable(() -> new int[]{PromiseSelection.days(changedWithinDays), PromiseSelection.limit(limit)}))
                .flatMap(window -> repository.findAll(organisationId).flatMapSequential(assessor::judge, JUDGED_AT_ONCE).map(RetrievePromisesUseCase::toResponse).collectList()
                        .map(all -> PromiseSelection.select(all, Instant.now(clock).minus(Duration.ofDays(window[0])), window[1])));
    }

    private static PromiseResponse toResponse(CapacityAssessor.Judged judged) {
        CapacityPolicy policy = judged.policy();
        Promise promise = CapacityPolicy.promise(judged.assessment());
        return new PromiseResponse(policy.getId(), policy.getName(), policy.getAssetId(), promise.verdict().name(), promise.code(), policy.getStatus().name(), policy.changedAt());
    }
}
