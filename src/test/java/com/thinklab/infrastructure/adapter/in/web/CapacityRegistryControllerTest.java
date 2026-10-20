package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateCapacityPolicyRequest;
import com.thinklab.application.dto.request.ReportCapacitySampleRequest;
import com.thinklab.application.dto.request.UpdateCapacityPolicyRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.CapacityPolicyResponse;
import com.thinklab.application.dto.response.CapacitySampleResponse;
import com.thinklab.application.usecase.ControlCapacityPolicyUseCase;
import com.thinklab.application.usecase.InitiateCapacityPolicyUseCase;
import com.thinklab.application.usecase.ReportCapacitySampleUseCase;
import com.thinklab.application.usecase.ReportCapacitySampleUseCase.Reported;
import com.thinklab.application.usecase.RetrieveCapacityPoliciesUseCase;
import com.thinklab.application.usecase.RetrieveCapacityPolicyAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveCapacityPolicyUseCase;
import com.thinklab.application.usecase.RetrieveCapacitySampleUseCase;
import com.thinklab.application.usecase.RetrieveCapacitySamplesUseCase;
import com.thinklab.application.usecase.RetrievePromisesUseCase;
import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.application.usecase.UpdateCapacityPolicyUseCase;
import com.thinklab.domain.model.CapacityPolicy.Forecast;
import com.thinklab.domain.model.CapacityPolicy.Level;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** The controller only reads headers and delegates: tenant, executor and role always travel to the use case. */
@ExtendWith(MockitoExtension.class)
class CapacityRegistryControllerTest {

    private static final String EXECUTOR = "op-1";
    private static final String ROLE = "ENGINEER";
    private final UUID tenant = UUID.randomUUID();
    private final String tenantHeader = tenant.toString();
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");

    @Mock private InitiateCapacityPolicyUseCase initiatePolicy;
    @Mock private RetrieveCapacityPolicyUseCase retrievePolicy;
    @Mock private RetrieveCapacityPoliciesUseCase retrievePolicies;
    @Mock private UpdateCapacityPolicyUseCase updatePolicy;
    @Mock private ControlCapacityPolicyUseCase controlPolicy;
    @Mock private RetrieveCapacityPolicyAuditLogUseCase policyAuditLog;
    @Mock private ReportCapacitySampleUseCase reportSample;
    @Mock private RetrieveCapacitySampleUseCase retrieveSample;
    @Mock private RetrieveCapacitySamplesUseCase retrieveSamples;
    @Mock private RetrievePromisesUseCase retrievePromises;

    private CapacityRegistryController controller() {
        return new CapacityRegistryController(initiatePolicy, retrievePolicy, retrievePolicies, updatePolicy, controlPolicy, policyAuditLog, reportSample, retrieveSample, retrieveSamples, retrievePromises);
    }

    private CapacityPolicyResponse policyResponse() {
        return new CapacityPolicyResponse(id, tenant, "Orders disk", UUID.randomUUID(), "DISK", 100.0, 70, 90, 30, 30, 24, "ACTIVE", "OK", 50.0, false, "STABLE", null, null, 50.0, now,
                now, now);
    }

    private CapacitySampleResponse sampleResponse() {
        return new CapacitySampleResponse(id, tenant, UUID.randomUUID(), 42.5, now, "tool", now);
    }

    @Test
    @DisplayName("initiate answers 201 with the policy")
    void initiate() {
        InitiateCapacityPolicyRequest request = new InitiateCapacityPolicyRequest("Orders disk", UUID.randomUUID(), Metric.DISK, 100.0, 70, 90, 30, 30, 24);
        when(initiatePolicy.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(policyResponse()));

        StepVerifier.create(controller().initiate(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("retrieve and the list answer the use cases' result with the filters")
    void retrieve() {
        when(retrievePolicy.execute(id, tenant, ROLE)).thenReturn(Mono.just(policyResponse()));
        when(retrievePolicies.execute(tenant, PolicyStatus.ACTIVE, null, Metric.DISK, Level.OK, Forecast.STABLE, ROLE)).thenReturn(Flux.just(policyResponse()));

        StepVerifier.create(controller().retrieve(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveAll(tenantHeader, ROLE, PolicyStatus.ACTIVE, null, Metric.DISK, Level.OK, Forecast.STABLE)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("update, pause and resume answer 204 and pass the right action")
    void changes() {
        UpdateCapacityPolicyRequest request = new UpdateCapacityPolicyRequest("N", UUID.randomUUID(), Metric.CPU, 8.0, 60, 80, 7, 14, 6);
        when(updatePolicy.execute(id, tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlPolicy.execute(id, tenant, ControlCapacityPolicyUseCase.Action.PAUSE, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlPolicy.execute(id, tenant, ControlCapacityPolicyUseCase.Action.RESUME, EXECUTOR, ROLE)).thenReturn(Mono.empty());

        StepVerifier.create(controller().update(id, tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().pause(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().resume(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("the audit log answers the trail")
    void auditLog() {
        when(policyAuditLog.execute(id, tenant, ROLE)).thenReturn(Mono.just(List.of(new AuditEntryResponse(now, "INITIATED", EXECUTOR, null, "ACTIVE", null))));

        StepVerifier.create(controller().auditLog(id, tenantHeader, ROLE)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("a new sample is 201 and one already reported is 200")
    void reportSampleStatus() {
        ReportCapacitySampleRequest request = new ReportCapacitySampleRequest(UUID.randomUUID(), 42.5, now);
        when(reportSample.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(new Reported(sampleResponse(), true))).thenReturn(Mono.just(new Reported(sampleResponse(), false)));

        StepVerifier.create(controller().reportSample(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().reportSample(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("one sample and the sample list, with the policy and the limit")
    void runs() {
        UUID policyId = UUID.randomUUID();
        when(retrieveSample.execute(id, tenant, ROLE)).thenReturn(Mono.just(sampleResponse()));
        when(retrieveSamples.execute(tenant, policyId, 10, ROLE)).thenReturn(Flux.just(sampleResponse()));

        StepVerifier.create(controller().retrieveSample(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveSamples(tenantHeader, ROLE, policyId, 10)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("the promises route passes the tenant, the role and the two parameters (platform ADR-001)")
    void promises() {
        when(retrievePromises.execute(tenant, 7, 50, ROLE)).thenReturn(Mono.just(List.of(new PromiseResponse(UUID.randomUUID(), "P", null, "KEPT", null, "ACTIVE", now))));

        StepVerifier.create(controller().promises(tenantHeader, ROLE, 7, 50)).assertNext(promises -> assertEquals(1, promises.size())).verifyComplete();
    }
}
