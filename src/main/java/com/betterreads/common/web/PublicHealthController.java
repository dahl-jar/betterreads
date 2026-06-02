package com.betterreads.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public health check endpoint. */
@RestController
@Tag(name = "Operations", description = "Public probes used by external monitors")
public class PublicHealthController {

    private static final String STATUS_UP = "UP";

    private static final String SERVICE_NAME = "betterreads";

    /**
     * Returns 200 with a JSON status body when the app is running.
     */
    @GetMapping("/healthz")
    @Operation(summary = "Health check")
    public HealthResponse healthz() {
        return new HealthResponse(STATUS_UP, SERVICE_NAME, Instant.now());
    }
}
