package com.betterreads.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Public health check response. */
public record HealthResponse(
    @Schema(example = "UP") String status,
    @Schema(example = "betterreads") String service,
    @Schema(example = "2026-06-02T10:15:30Z") Instant timestamp
) { }
