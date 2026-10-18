package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.ReportCapacitySampleRequest;
import com.thinklab.application.dto.response.CapacitySampleResponse;
import com.thinklab.application.mapper.CapacityMapper;
import com.thinklab.domain.exception.CapacityPolicyNotFoundException;
import com.thinklab.domain.model.CapacitySample;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.CapacityPolicyRepository;
import com.thinklab.domain.repository.CapacitySampleRepository;
import com.thinklab.domain.repository.CapacitySampleRepository.Stored;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for what the tool reports when it measures (BIAN Behavior Qualifier: {@code sample/initiate}), ADR-030. Idempotent: the same policy
 * and moment is the same sample, so a tool may retry; the stored sample comes back with {@code created} false. Either way the policy's latest
 * sample is folded again (it only moves forward in time), so a report that stored the sample and died before that update is repaired by the
 * retry. A sovereign id is spent only for a sample that is new.
 */
@Singleton
public class ReportCapacitySampleUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReportCapacitySampleUseCase.class);

    /** The sample as stored, and whether this report created it (201) or found it already there (200). */
    public record Reported(CapacitySampleResponse sample, boolean created) {
    }

    private final HashServicePort hashServicePort;
    private final CapacityPolicyRepository policies;
    private final CapacitySampleRepository samples;
    private final Clock clock;

    public ReportCapacitySampleUseCase(HashServicePort hashServicePort, CapacityPolicyRepository policies, CapacitySampleRepository samples, Clock clock) {
        this.hashServicePort = hashServicePort;
        this.policies = policies;
        this.samples = samples;
        this.clock = clock;
    }

    public Mono<Reported> execute(UUID organisationId, ReportCapacitySampleRequest request, String executor, String role) {
        log.info("[USE CASE] Reporting a sample for CapacityPolicy ID: {}", request.policyId());

        return Mono.fromRunnable(() -> CapacityAccess.requireStaff(role, "report a capacity sample"))
                .then(Mono.defer(() -> policies.findById(request.policyId(), organisationId)))
                .switchIfEmpty(Mono.error(new CapacityPolicyNotFoundException("CapacityPolicy " + request.policyId() + " not found.")))
                .flatMap(policy -> samples.findByNaturalKey(organisationId, policy.getId(), CapacitySample.truncate(request.measuredAt()))
                        .map(existing -> new Stored(existing, false))
                        .switchIfEmpty(Mono.defer(() -> store(organisationId, request, executor))))
                .flatMap(stored -> policies.recordSample(request.policyId(), organisationId, stored.sample())
                        .thenReturn(new Reported(CapacityMapper.toResponse(stored.sample()), stored.created())));
    }

    private Mono<Stored> store(UUID organisationId, ReportCapacitySampleRequest request, String executor) {
        return hashServicePort.generateSovereignId("capacity-sample-creation")
                .map(id -> CapacitySample.createNew(id, organisationId, request.policyId(), request.value(), request.measuredAt(), executor, Instant.now(clock)))
                .flatMap(samples::create);
    }
}
