package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateCapacityPolicyRequest;
import com.thinklab.application.dto.request.ReportCapacitySampleRequest;
import com.thinklab.application.dto.request.UpdateCapacityPolicyRequest;
import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.CapacityPolicyResponse;
import com.thinklab.application.dto.response.CapacitySampleResponse;
import com.thinklab.application.usecase.RetrievePromisesUseCase;
import com.thinklab.application.usecase.ControlCapacityPolicyUseCase;
import com.thinklab.application.usecase.InitiateCapacityPolicyUseCase;
import com.thinklab.application.usecase.ReportCapacitySampleUseCase;
import com.thinklab.application.usecase.RetrieveCapacityPoliciesUseCase;
import com.thinklab.application.usecase.RetrieveCapacityPolicyAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveCapacityPolicyUseCase;
import com.thinklab.application.usecase.RetrieveCapacitySampleUseCase;
import com.thinklab.application.usecase.RetrieveCapacitySamplesUseCase;
import com.thinklab.application.usecase.UpdateCapacityPolicyUseCase;
import com.thinklab.domain.model.CapacityPolicy.Forecast;
import com.thinklab.domain.model.CapacityPolicy.Level;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-capacity-registry} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.CapacityPolicy} is the Control Record, at the root;
 * {@link com.thinklab.domain.model.CapacitySample} is the secondary aggregate, under {@code /sample}: facts the tool reports, never edited, never
 * deleted. There is no {@code DELETE}: a policy is paused.
 *
 * <p><b>Tenant on every route, staff only (ADR-032):</b> {@code X-Tenant-Id} scopes every lookup (another tenant's policy is a 404) and
 * {@code X-Role} makes every route refuse a {@code REQUESTER} with 403 {@code ERR-CAP-00403}.
 */
@Controller("/it-capacity-registry/v1")
public class CapacityRegistryController {

    private static final Logger log = LoggerFactory.getLogger(CapacityRegistryController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateCapacityPolicyUseCase initiatePolicy;
    private final RetrieveCapacityPolicyUseCase retrievePolicy;
    private final RetrieveCapacityPoliciesUseCase retrievePolicies;
    private final UpdateCapacityPolicyUseCase updatePolicy;
    private final ControlCapacityPolicyUseCase controlPolicy;
    private final RetrieveCapacityPolicyAuditLogUseCase policyAuditLog;
    private final ReportCapacitySampleUseCase reportSample;
    private final RetrieveCapacitySampleUseCase retrieveSample;
    private final RetrieveCapacitySamplesUseCase retrieveSamples;

    private final RetrievePromisesUseCase retrievePromises;

    public CapacityRegistryController(InitiateCapacityPolicyUseCase initiatePolicy, RetrieveCapacityPolicyUseCase retrievePolicy, RetrieveCapacityPoliciesUseCase retrievePolicies,
                                    UpdateCapacityPolicyUseCase updatePolicy, ControlCapacityPolicyUseCase controlPolicy, RetrieveCapacityPolicyAuditLogUseCase policyAuditLog,
                                    ReportCapacitySampleUseCase reportSample, RetrieveCapacitySampleUseCase retrieveSample, RetrieveCapacitySamplesUseCase retrieveSamples,
            RetrievePromisesUseCase retrievePromises) {
        this.retrievePromises = retrievePromises;
        this.initiatePolicy = initiatePolicy;
        this.retrievePolicy = retrievePolicy;
        this.retrievePolicies = retrievePolicies;
        this.updatePolicy = updatePolicy;
        this.controlPolicy = controlPolicy;
        this.policyAuditLog = policyAuditLog;
        this.reportSample = reportSample;
        this.retrieveSample = retrieveSample;
        this.retrieveSamples = retrieveSamples;
    }

    // ------------------------------------------------------------------ Policies

    /** Behavior Qualifier: {@code initiate}. What is promised about an asset. */
    @Post("/initiate")
    public Mono<HttpResponse<CapacityPolicyResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid InitiateCapacityPolicyRequest request
    ) {
        log.info("[ACTION: INITIATE_BACKUP_POLICY] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return initiatePolicy.execute(UUID.fromString(tenantId), request, executor, role).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. One policy of the tenant, judged now. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<CapacityPolicyResponse>> retrieve(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrievePolicy.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection), oldest first, filterable by status, asset, metric, level and forecast. */
    @Get("/retrieve")
    public Mono<List<CapacityPolicyResponse>> retrieveAll(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                        @QueryValue @Nullable PolicyStatus status, @QueryValue @Nullable UUID assetId, @QueryValue @Nullable Metric metric,
                                                        @QueryValue @Nullable Level level, @QueryValue @Nullable Forecast forecast) {
        return Mono.defer(() -> retrievePolicies.execute(UUID.fromString(tenantId), status, assetId, metric, level, forecast, role).collectList());
    }

    /**
     * Behavior Qualifier: {@code promises/retrieve} (platform ADR-001, the promises contract). Every broken promise and every one that changed within the days asked
     * (30 unless asked, 1 to 365), at most {@code limit} of each (500 unless asked, at most 1000).
     */
    @Get("/promises/retrieve")
    public Mono<List<PromiseResponse>> promises(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                @QueryValue @Nullable Integer changedWithinDays, @QueryValue @Nullable Integer limit) {
        return Mono.defer(() -> retrievePromises.execute(UUID.fromString(tenantId), changedWithinDays, limit, role));
    }

    /** Behavior Qualifier: {@code update}. The whole definition again. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateCapacityPolicyRequest request
    ) {
        return Mono.defer(() -> updatePolicy.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/pause}. ACTIVE -&gt; PAUSED: the policy is no longer judged. */
    @Put("/{id}/control/pause")
    public Mono<HttpResponse<Void>> pause(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                          @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlCapacityPolicyUseCase.Action.PAUSE, executor, role);
    }

    /** Behavior Qualifier: {@code control/resume}. PAUSED -&gt; ACTIVE. */
    @Put("/{id}/control/resume")
    public Mono<HttpResponse<Void>> resume(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                           @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlCapacityPolicyUseCase.Action.RESUME, executor, role);
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> auditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> policyAuditLog.execute(id, UUID.fromString(tenantId), role));
    }

    // ------------------------------------------------------------------ Samples

    /** Behavior Qualifier: {@code sample/initiate}. What the tool reports when it measures: 201 for a new sample, 200 for one already reported. */
    @Post("/sample/initiate")
    public Mono<HttpResponse<CapacitySampleResponse>> reportSample(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid ReportCapacitySampleRequest request
    ) {
        log.info("[ACTION: REPORT_BACKUP_RUN] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return reportSample.execute(UUID.fromString(tenantId), request, executor, role)
                .map(reported -> reported.created() ? HttpResponse.created(reported.sample()) : HttpResponse.ok(reported.sample()));
    }

    /** Behavior Qualifier: {@code sample/retrieve}. One sample of the tenant. */
    @Get("/sample/{id}/retrieve")
    public Mono<HttpResponse<CapacitySampleResponse>> retrieveSample(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveSample.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code sample/retrieve} (collection), newest first, at most 200 (50 unless asked). */
    @Get("/sample/retrieve")
    public Mono<List<CapacitySampleResponse>> retrieveSamples(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                      @QueryValue @Nullable UUID policyId, @QueryValue @Nullable Integer limit) {
        return Mono.defer(() -> retrieveSamples.execute(UUID.fromString(tenantId), policyId, limit, role).collectList());
    }

    private Mono<HttpResponse<Void>> control(UUID id, String tenantId, ControlCapacityPolicyUseCase.Action action, String executor, String role) {
        log.info("[ACTION: {}_BACKUP_POLICY] [EXECUTOR: {}] for ID: {}", action, executor, id);

        return Mono.defer(() -> controlPolicy.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }
}
