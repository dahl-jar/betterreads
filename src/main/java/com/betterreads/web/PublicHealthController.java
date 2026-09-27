package com.betterreads.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Operations", description = "Service health")
@SecurityRequirements
class PublicHealthController {

    private static final String STATUS_UP = "UP";

    private static final String SERVICE_NAME = "betterreads";

    @GetMapping("/healthz")
    @Operation(summary = "Health check")
    public HealthResponse healthz() {
        return new HealthResponse(STATUS_UP, SERVICE_NAME, Instant.now());
    }
}
