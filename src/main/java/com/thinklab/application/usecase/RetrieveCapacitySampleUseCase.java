package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.CapacitySampleResponse;
import com.thinklab.application.mapper.CapacityMapper;
import com.thinklab.domain.exception.CapacitySampleNotFoundException;
import com.thinklab.domain.repository.CapacitySampleRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reading one sample of the tenant (BIAN Behavior Qualifier: {@code sample/retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveCapacitySampleUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveCapacitySampleUseCase.class);

    private final CapacitySampleRepository repository;

    public RetrieveCapacitySampleUseCase(CapacitySampleRepository repository) {
        this.repository = repository;
    }

    public Mono<CapacitySampleResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving CapacitySample ID: {}", id);

        return Mono.fromRunnable(() -> CapacityAccess.requireStaff(role, "read a capacity sample"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new CapacitySampleNotFoundException("CapacitySample " + id + " not found.")))
                .map(CapacityMapper::toResponse);
    }
}
