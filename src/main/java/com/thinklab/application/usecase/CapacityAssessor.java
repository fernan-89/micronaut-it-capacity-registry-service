package com.thinklab.application.usecase;

import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.Assessment;
import com.thinklab.domain.model.CapacityPolicy.Point;
import com.thinklab.domain.repository.CapacitySampleRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Judges a policy now (ADR-031): reads the samples of its trend window and lets the policy assess itself. Nothing is stored. */
@Singleton
public class CapacityAssessor {

    /** A policy and how it was judged. */
    public record Judged(CapacityPolicy policy, Assessment assessment) {
    }

    private final CapacitySampleRepository samples;
    private final Clock clock;

    public CapacityAssessor(CapacitySampleRepository samples, Clock clock) {
        this.samples = samples;
        this.clock = clock;
    }

    public Mono<Judged> judge(CapacityPolicy policy) {
        return Mono.defer(() -> {
            Instant now = Instant.now(clock);
            return samples.findSince(policy.getOrganisationId(), policy.getId(), now.minus(Duration.ofDays(policy.getWindowDays())))
                    .map(sample -> new Point(sample.getMeasuredAt(), sample.getValue()))
                    .collectList()
                    .map(points -> new Judged(policy, policy.assess(now, points)));
        });
    }
}
