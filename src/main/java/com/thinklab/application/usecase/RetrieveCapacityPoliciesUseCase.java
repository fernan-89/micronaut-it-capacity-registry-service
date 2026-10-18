package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.CapacityPolicyResponse;
import com.thinklab.application.mapper.CapacityMapper;
import com.thinklab.domain.model.CapacityPolicy.Forecast;
import com.thinklab.domain.model.CapacityPolicy.Level;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import com.thinklab.domain.repository.CapacityPolicyRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for listing the tenant's policies, oldest first, judged now, filterable by status, asset, metric, level and forecast (BIAN Behavior
 * Qualifier: {@code retrieve}, collection). The level and the forecast are judged when read, so they are filtered in memory over the tenant's
 * policies (ADR-031); the order of the policies is kept.
 */
@Singleton
public class RetrieveCapacityPoliciesUseCase {

    static final int JUDGED_AT_ONCE = 4;
    private static final Logger log = LoggerFactory.getLogger(RetrieveCapacityPoliciesUseCase.class);

    private final CapacityPolicyRepository repository;
    private final CapacityAssessor assessor;

    public RetrieveCapacityPoliciesUseCase(CapacityPolicyRepository repository, CapacityAssessor assessor) {
        this.repository = repository;
        this.assessor = assessor;
    }

    public Flux<CapacityPolicyResponse> execute(UUID organisationId, PolicyStatus status, UUID assetId, Metric metric, Level level, Forecast forecast, String role) {
        log.info("[USE CASE] Retrieving the CapacityPolicies of organisation: {}", organisationId);

        return Mono.fromRunnable(() -> CapacityAccess.requireStaff(role, "list capacity policies"))
                .thenMany(Flux.defer(() -> repository.findAll(organisationId)
                        .filter(policy -> status == null || policy.getStatus() == status)
                        .filter(policy -> assetId == null || assetId.equals(policy.getAssetId()))
                        .filter(policy -> metric == null || policy.getMetric() == metric)
                        .flatMapSequential(assessor::judge, JUDGED_AT_ONCE)
                        .filter(judged -> level == null || judged.assessment().level() == level)
                        .filter(judged -> forecast == null || judged.assessment().forecast() == forecast)
                        .map(judged -> CapacityMapper.toResponse(judged.policy(), judged.assessment()))));
    }
}
