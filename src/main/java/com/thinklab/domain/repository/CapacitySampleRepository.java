package com.thinklab.domain.repository;

import com.thinklab.domain.model.CapacitySample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Outbound Port for CapacitySample persistence: samples are facts, inserted once and never changed. Every lookup is tenant-scoped. */
public interface CapacitySampleRepository {

    /** The most samples one trend is drawn from: the newest of the window if there are more. */
    int MAX_TREND_POINTS = 1000;

    /**
     * Inserts the sample. The same policy and moment is the same sample (a unique index): the loser of a repeated or simultaneous report gets the
     * stored one back and {@code created} false, so a tool may retry a report as often as it likes.
     */
    Mono<Stored> create(CapacitySample sample);

    /** The sample already reported for that policy and moment, if any. */
    Mono<CapacitySample> findByNaturalKey(UUID organisationId, UUID policyId, Instant measuredAt);

    Mono<CapacitySample> findById(UUID id, UUID organisationId);

    /** The tenant's samples, newest first, at most {@code limit}, optionally of one policy. */
    Flux<CapacitySample> findAll(UUID organisationId, UUID policyId, int limit);

    /** The samples of a policy taken at or after {@code since}, oldest first, at most {@value #MAX_TREND_POINTS} (the newest ones). */
    Flux<CapacitySample> findSince(UUID organisationId, UUID policyId, Instant since);

    record Stored(CapacitySample sample, boolean created) {
    }
}
