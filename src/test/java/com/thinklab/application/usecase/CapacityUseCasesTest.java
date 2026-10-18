package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateCapacityPolicyRequest;
import com.thinklab.application.dto.request.ReportCapacitySampleRequest;
import com.thinklab.application.dto.request.UpdateCapacityPolicyRequest;
import com.thinklab.domain.exception.CapacityAccessDeniedException;
import com.thinklab.domain.exception.CapacityPolicyNotFoundException;
import com.thinklab.domain.exception.CapacitySampleNotFoundException;
import com.thinklab.domain.exception.InvalidCapacityPolicyStatusException;
import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.Forecast;
import com.thinklab.domain.model.CapacityPolicy.Level;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import com.thinklab.domain.model.CapacitySample;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.CapacityPolicyRepository;
import com.thinklab.domain.repository.CapacitySampleRepository;
import com.thinklab.domain.repository.CapacitySampleRepository.Stored;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapacityUseCasesTest {

    private static final String STAFF = "ENGINEER";
    private static final String REQUESTER = "REQUESTER";
    private static final UUID ELSEWHERE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UUID org = UUID.randomUUID();
    private final UUID asset = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private HashServicePort hash;
    private CapacityPolicyRepository policies;
    private CapacitySampleRepository samples;
    private CapacityAssessor assessor;

    @BeforeEach
    void setUp() {
        hash = mock(HashServicePort.class);
        policies = mock(CapacityPolicyRepository.class);
        samples = mock(CapacitySampleRepository.class);
        when(samples.findSince(any(), any(), any())).thenReturn(Flux.empty());
        assessor = new CapacityAssessor(samples, clock);
    }

    private CapacityPolicy policy() {
        return CapacityPolicy.createNew(UUID.randomUUID(), org, "Orders disk", asset, Metric.DISK, 100, 70, 90, 30, 30, 24, "op");
    }

    /** A policy whose latest sample is 95 of 100 (critical). */
    private CapacityPolicy critical(UUID policyAsset) {
        return CapacityPolicy.reconstitute(UUID.randomUUID(), org, "Full disk", policyAsset, Metric.DISK, 100, 70, 90, 30, 30, 24, PolicyStatus.ACTIVE, now, now, 95.0, now, List.of());
    }

    private CapacitySample sample(UUID policyId, double value, int daysAgo) {
        return CapacitySample.createNew(UUID.randomUUID(), org, policyId, value, now.minus(Duration.ofDays(daysAgo)), "tool", now);
    }

    private InitiateCapacityPolicyRequest initiateRequest() {
        return new InitiateCapacityPolicyRequest("Orders disk", asset, Metric.DISK, 100.0, 70, 90, 30, 30, 24);
    }

    private UpdateCapacityPolicyRequest updateRequest() {
        return new UpdateCapacityPolicyRequest("Renamed", asset, Metric.MEMORY, 64.0, 50, 80, 14, 60, 12);
    }

    private ReportCapacitySampleRequest report(UUID policyId) {
        return new ReportCapacitySampleRequest(policyId, 42.5, now.minusSeconds(60));
    }

    @Test
    @DisplayName("the access rule refuses a requester and lets staff and a missing role through; it is a utility class")
    void access() throws ReflectiveOperationException {
        CapacityAccess.requireStaff(STAFF, "x");
        CapacityAccess.requireStaff(null, "x");
        CapacityAccessDeniedException denied = assertThrows(CapacityAccessDeniedException.class, () -> CapacityAccess.requireStaff(REQUESTER, "do it"));
        assertTrue(denied.getMessage().contains("do it"));
        Constructor<CapacityAccess> constructor = CapacityAccess.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("initiate spends a sovereign id, stores the policy and answers it judged now (no data yet); a requester spends nothing")
    void initiate() {
        UUID id = UUID.randomUUID();
        when(hash.generateSovereignId("capacity-policy-creation")).thenReturn(Mono.just(id));
        when(policies.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        InitiateCapacityPolicyUseCase useCase = new InitiateCapacityPolicyUseCase(hash, policies, clock);

        StepVerifier.create(useCase.execute(org, initiateRequest(), "op", STAFF)).assertNext(response -> {
            assertEquals(id, response.id());
            assertEquals("ACTIVE", response.status());
            assertEquals("NO_DATA", response.level());
            assertEquals("DISK", response.metric());
        }).verifyComplete();

        StepVerifier.create(useCase.execute(org, initiateRequest(), "op", REQUESTER)).expectError(CapacityAccessDeniedException.class).verify();
        verify(hash).generateSovereignId("capacity-policy-creation");
    }

    @Test
    @DisplayName("retrieve reads the samples of the policy's window, judges the trend, answers the tenant's policy, a 404 otherwise, and refuses a requester")
    void retrieve() {
        CapacityPolicy policy = CapacityPolicy.reconstitute(UUID.randomUUID(), org, "Orders disk", asset, Metric.DISK, 100, 70, 90, 30, 30, 24, PolicyStatus.ACTIVE, now, now, 60.0, now, List.of());
        when(policies.findById(policy.getId(), org)).thenReturn(Mono.just(policy));
        when(policies.findById(any(), eq(ELSEWHERE))).thenReturn(Mono.empty());
        when(samples.findSince(eq(org), eq(policy.getId()), any())).thenReturn(Flux.just(sample(policy.getId(), 40, 2), sample(policy.getId(), 50, 1), sample(policy.getId(), 60, 0)));
        RetrieveCapacityPolicyUseCase useCase = new RetrieveCapacityPolicyUseCase(policies, assessor);

        StepVerifier.create(useCase.execute(policy.getId(), org, STAFF)).assertNext(response -> {
            assertEquals("Orders disk", response.name());
            assertEquals("OK", response.level());
            assertEquals("WITHIN_HORIZON", response.forecast());
            assertEquals(4.0, response.daysToLimit(), 1e-9);
        }).verifyComplete();
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(samples).findSince(eq(org), eq(policy.getId()), since.capture());
        assertEquals(now.minus(Duration.ofDays(30)), since.getValue());
        StepVerifier.create(useCase.execute(policy.getId(), ELSEWHERE, STAFF)).expectError(CapacityPolicyNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(policy.getId(), org, REQUESTER)).expectError(CapacityAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the list keeps its order and filters by status, asset, metric, level and forecast, judged now; it refuses a requester")
    void retrieveAll() {
        CapacityPolicy empty = policy();
        CapacityPolicy paused = CapacityPolicy.createNew(UUID.randomUUID(), org, "Cpu", UUID.randomUUID(), Metric.CPU, 8, 60, 80, 7, 14, 6, "op");
        paused.pause("op");
        UUID fullAsset = UUID.randomUUID();
        CapacityPolicy full = critical(fullAsset);
        when(policies.findAll(org)).thenReturn(Flux.just(empty, paused, full));
        when(samples.findSince(eq(org), eq(full.getId()), any())).thenReturn(Flux.just(sample(full.getId(), 85, 2), sample(full.getId(), 90, 1), sample(full.getId(), 95, 0)));
        RetrieveCapacityPoliciesUseCase useCase = new RetrieveCapacityPoliciesUseCase(policies, assessor);

        StepVerifier.create(useCase.execute(org, null, null, null, null, null, STAFF)).assertNext(r -> assertEquals(empty.getId(), r.id()))
                .assertNext(r -> assertEquals(paused.getId(), r.id())).assertNext(r -> assertEquals(full.getId(), r.id())).verifyComplete();
        StepVerifier.create(useCase.execute(org, PolicyStatus.PAUSED, null, null, null, null, STAFF)).assertNext(r -> assertEquals(paused.getId(), r.id())).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, fullAsset, null, null, null, STAFF)).assertNext(r -> assertEquals(full.getId(), r.id())).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, Metric.CPU, null, null, STAFF)).assertNext(r -> assertEquals(paused.getId(), r.id())).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, Level.CRITICAL, null, STAFF)).assertNext(r -> assertEquals(full.getId(), r.id())).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, Level.WARNING, null, STAFF)).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, null, Forecast.WITHIN_HORIZON, STAFF)).assertNext(r -> assertEquals(full.getId(), r.id())).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, null, Forecast.STABLE, STAFF)).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, null, null, REQUESTER)).expectError(CapacityAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("update and control load the policy, apply the change and save it with the status loaded; an unknown policy is a 404 and a requester is refused")
    void updateAndControl() {
        CapacityPolicy policy = policy();
        when(policies.findById(policy.getId(), org)).thenReturn(Mono.just(policy));
        when(policies.findById(any(), eq(ELSEWHERE))).thenReturn(Mono.empty());
        when(policies.save(any(), any(), any())).thenReturn(Mono.empty());
        CapacityPolicyWorkflow workflow = new CapacityPolicyWorkflow(policies);
        UpdateCapacityPolicyUseCase update = new UpdateCapacityPolicyUseCase(workflow);
        ControlCapacityPolicyUseCase control = new ControlCapacityPolicyUseCase(workflow);

        StepVerifier.create(update.execute(policy.getId(), org, updateRequest(), "op", STAFF)).verifyComplete();
        assertEquals("Renamed", policy.getName());
        assertEquals(Metric.MEMORY, policy.getMetric());
        assertEquals(64.0, policy.getLimit());
        StepVerifier.create(control.execute(policy.getId(), org, ControlCapacityPolicyUseCase.Action.PAUSE, "op", STAFF)).verifyComplete();
        assertEquals(PolicyStatus.PAUSED, policy.getStatus());
        StepVerifier.create(control.execute(policy.getId(), org, ControlCapacityPolicyUseCase.Action.PAUSE, "op", STAFF)).expectError(InvalidCapacityPolicyStatusException.class).verify();
        StepVerifier.create(control.execute(policy.getId(), org, ControlCapacityPolicyUseCase.Action.RESUME, "op", STAFF)).verifyComplete();
        assertEquals(PolicyStatus.ACTIVE, policy.getStatus());

        ArgumentCaptor<PolicyStatus> expected = ArgumentCaptor.forClass(PolicyStatus.class);
        verify(policies, times(3)).save(any(), expected.capture(), any());
        assertEquals(List.of(PolicyStatus.ACTIVE, PolicyStatus.ACTIVE, PolicyStatus.PAUSED), expected.getAllValues());

        StepVerifier.create(update.execute(policy.getId(), ELSEWHERE, updateRequest(), "op", STAFF)).expectError(CapacityPolicyNotFoundException.class).verify();
        StepVerifier.create(update.execute(policy.getId(), org, updateRequest(), "op", REQUESTER)).expectError(CapacityAccessDeniedException.class).verify();
        StepVerifier.create(control.execute(policy.getId(), org, ControlCapacityPolicyUseCase.Action.PAUSE, "op", REQUESTER)).expectError(CapacityAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the audit log is the policy's trail, a 404 for an unknown policy, and staff only")
    void auditLog() {
        CapacityPolicy policy = policy();
        policy.pause("op");
        when(policies.findById(policy.getId(), org)).thenReturn(Mono.just(policy));
        when(policies.findById(any(), eq(ELSEWHERE))).thenReturn(Mono.empty());
        RetrieveCapacityPolicyAuditLogUseCase useCase = new RetrieveCapacityPolicyAuditLogUseCase(policies);

        StepVerifier.create(useCase.execute(policy.getId(), org, STAFF)).assertNext(entries -> {
            assertEquals(2, entries.size());
            assertEquals("INITIATED", entries.get(0).action());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(policy.getId(), ELSEWHERE, STAFF)).expectError(CapacityPolicyNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(policy.getId(), org, REQUESTER)).expectError(CapacityAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("a new sample is stored and folded into the policy; the answer says it was created")
    void reportNew() {
        CapacityPolicy policy = policy();
        UUID sampleId = UUID.randomUUID();
        when(policies.findById(policy.getId(), org)).thenReturn(Mono.just(policy));
        when(samples.findByNaturalKey(eq(org), eq(policy.getId()), any())).thenReturn(Mono.empty());
        when(hash.generateSovereignId("capacity-sample-creation")).thenReturn(Mono.just(sampleId));
        when(samples.create(any())).thenAnswer(invocation -> Mono.just(new Stored(invocation.getArgument(0), true)));
        when(policies.recordSample(eq(policy.getId()), eq(org), any())).thenReturn(Mono.empty());
        ReportCapacitySampleUseCase useCase = new ReportCapacitySampleUseCase(hash, policies, samples, clock);

        StepVerifier.create(useCase.execute(org, report(policy.getId()), "tool", STAFF)).assertNext(reported -> {
            assertTrue(reported.created());
            assertEquals(sampleId, reported.sample().id());
            assertEquals(42.5, reported.sample().value());
            assertEquals("tool", reported.sample().reportedBy());
        }).verifyComplete();
        verify(policies).recordSample(eq(policy.getId()), eq(org), any(CapacitySample.class));
    }

    @Test
    @DisplayName("the same sample reported again is the stored one, no id is spent, and the policy's latest sample is folded again")
    void reportAgain() {
        CapacityPolicy policy = policy();
        CapacitySample stored = sample(policy.getId(), 42.5, 0);
        when(policies.findById(policy.getId(), org)).thenReturn(Mono.just(policy));
        when(samples.findByNaturalKey(eq(org), eq(policy.getId()), any())).thenReturn(Mono.just(stored));
        when(policies.recordSample(any(), any(), any())).thenReturn(Mono.empty());
        ReportCapacitySampleUseCase useCase = new ReportCapacitySampleUseCase(hash, policies, samples, clock);

        StepVerifier.create(useCase.execute(org, report(policy.getId()), "tool", STAFF)).assertNext(reported -> {
            assertFalse(reported.created());
            assertEquals(stored.getId(), reported.sample().id());
        }).verifyComplete();
        verify(hash, never()).generateSovereignId(any());
        verify(samples, never()).create(any());
        verify(policies).recordSample(eq(policy.getId()), eq(org), eq(stored));
    }

    @Test
    @DisplayName("the lookup uses the moment cut to the millisecond, like the stored one")
    void reportLooksUpTheTruncatedMoment() {
        CapacityPolicy policy = policy();
        when(policies.findById(policy.getId(), org)).thenReturn(Mono.just(policy));
        when(samples.findByNaturalKey(any(), any(), any())).thenReturn(Mono.just(sample(policy.getId(), 1, 0)));
        when(policies.recordSample(any(), any(), any())).thenReturn(Mono.empty());
        ReportCapacitySampleUseCase useCase = new ReportCapacitySampleUseCase(hash, policies, samples, clock);
        Instant precise = Instant.parse("2026-10-18T11:00:00.123456789Z");

        StepVerifier.create(useCase.execute(org, new ReportCapacitySampleRequest(policy.getId(), 1.0, precise), "tool", STAFF)).expectNextCount(1).verifyComplete();

        verify(samples).findByNaturalKey(org, policy.getId(), Instant.parse("2026-10-18T11:00:00.123Z"));
    }

    @Test
    @DisplayName("a sample for a policy that is not the tenant's is a 404, a requester is refused, and a sample from the future is a 400")
    void reportRefused() {
        UUID unknown = UUID.randomUUID();
        when(policies.findById(unknown, org)).thenReturn(Mono.empty());
        CapacityPolicy policy = policy();
        when(policies.findById(policy.getId(), org)).thenReturn(Mono.just(policy));
        when(samples.findByNaturalKey(any(), any(), any())).thenReturn(Mono.empty());
        when(hash.generateSovereignId(any())).thenReturn(Mono.just(UUID.randomUUID()));
        ReportCapacitySampleUseCase useCase = new ReportCapacitySampleUseCase(hash, policies, samples, clock);

        StepVerifier.create(useCase.execute(org, report(unknown), "tool", STAFF)).expectError(CapacityPolicyNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(org, report(unknown), "tool", REQUESTER)).expectError(CapacityAccessDeniedException.class).verify();
        ReportCapacitySampleRequest future = new ReportCapacitySampleRequest(policy.getId(), 1.0, now.plus(Duration.ofHours(1)));
        StepVerifier.create(useCase.execute(org, future, "tool", STAFF)).expectError(IllegalArgumentException.class).verify();
        verify(samples, never()).create(any());
    }

    @Test
    @DisplayName("one sample: answered for the tenant, a 404 otherwise, staff only")
    void retrieveSample() {
        CapacitySample stored = sample(UUID.randomUUID(), 5, 0);
        when(samples.findById(stored.getId(), org)).thenReturn(Mono.just(stored));
        when(samples.findById(any(), eq(ELSEWHERE))).thenReturn(Mono.empty());
        RetrieveCapacitySampleUseCase useCase = new RetrieveCapacitySampleUseCase(samples);

        StepVerifier.create(useCase.execute(stored.getId(), org, STAFF)).assertNext(response -> assertEquals(5.0, response.value())).verifyComplete();
        StepVerifier.create(useCase.execute(stored.getId(), ELSEWHERE, STAFF)).expectError(CapacitySampleNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(stored.getId(), org, REQUESTER)).expectError(CapacityAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the sample list uses 50 unless asked, accepts 1 to 200, refuses anything else and a requester")
    void retrieveSamples() {
        UUID policyId = UUID.randomUUID();
        when(samples.findAll(eq(org), eq(policyId), any(Integer.class))).thenReturn(Flux.just(sample(policyId, 5, 0)));
        RetrieveCapacitySamplesUseCase useCase = new RetrieveCapacitySamplesUseCase(samples);

        StepVerifier.create(useCase.execute(org, policyId, null, STAFF)).expectNextCount(1).verifyComplete();
        verify(samples).findAll(org, policyId, 50);
        StepVerifier.create(useCase.execute(org, policyId, 1, STAFF)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(org, policyId, 200, STAFF)).expectNextCount(1).verifyComplete();
        verify(samples).findAll(org, policyId, 200);
        StepVerifier.create(useCase.execute(org, policyId, 0, STAFF)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(useCase.execute(org, policyId, 201, STAFF)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(useCase.execute(org, policyId, null, REQUESTER)).expectError(CapacityAccessDeniedException.class).verify();
    }
}
