package com.thinklab.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

/**
 * Infrastructure Component: OpenAPI 3.0 Documentation Metadata.
 *
 * <p><b>Architectural Role:</b>
 * Centralizes the global API contract definition. During the Ahead-of-Time (AOT)
 * compilation phase, the 'micronaut-openapi' AST processor parses these annotations
 * to statically generate the official swagger.yml specification.
 *
 * @author Thinklab Core Infrastructure Team
 * @version 1.0.0
 * @since 1.0
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Thinklab IT Capacity Registry Service Domain",
                version = "v1.0.0",
                description = "BIAN-aligned Service Domain (Control Record: CapacityPolicy) for the IT capacity registry: how much of a metric an asset may use (limit, warning and critical utilisation), the samples the tools report, and where each asset stands right now, with a simple linear forecast of when it would run out. All routes follow the /it-capacity-registry/v1/{behavior-qualifier} convention (initiate, retrieve, update, control, audit-log, sample). Illegal state transitions and duplicates are HTTP 409 with an RFC 7807 problem document.",
                contact = @Contact(
                        name = "Thinklab SRE & Security Operations",
                        email = "sre-core@thinklab.com",
                        url = "https://engineering.thinklab.com"
                ),
                license = @License(
                        name = "Proprietary & Confidential - Thinklab Internal Only",
                        url = "https://thinklab.com/security/compliance"
                )
        )
)
public class OpenApiConfig {
    // Empty class serving strictly as an AST metadata anchor for Swagger generation.
}
