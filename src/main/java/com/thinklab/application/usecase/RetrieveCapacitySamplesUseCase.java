package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.CapacitySampleResponse;
import com.thinklab.application.mapper.CapacityMapper;
import com.thinklab.domain.repository.CapacitySampleRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for listing the tenant's samples, newest first, optionally of one policy, at most {@value #MAX_LIMIT} (BIAN Behavior Qualifier: {@code sample/retrieve}, collection). */
@Singleton
public class RetrieveCapacitySamplesUseCase {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private static final Logger log = LoggerFactory.getLogger(RetrieveCapacitySamplesUseCase.class);

    private final CapacitySampleRepository repository;

    public RetrieveCapacitySamplesUseCase(CapacitySampleRepository repository) {
        this.repository = repository;
    }

    public Flux<CapacitySampleResponse> execute(UUID organisationId, UUID policyId, Integer limit, String role) {
        log.info("[USE CASE] Retrieving the CapacitySamples of organisation: {}", organisationId);

        return Mono.fromRunnable(() -> CapacityAccess.requireStaff(role, "list capacity samples"))
                .then(Mono.fromCallable(() -> effectiveLimit(limit)))
                .flatMapMany(effective -> repository.findAll(organisationId, policyId, effective))
                .map(CapacityMapper::toResponse);
    }

    private static int effectiveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("The limit must be between 1 and " + MAX_LIMIT + ".");
        }
        return limit;
    }
}
