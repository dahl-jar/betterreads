package com.betterreads.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness endpoint for external monitors. Component health lives behind the actuator
 * on the management port.
 */
@RestController
@Tag(name = "Operations", description = "Public probes used by external monitors")
public class PublicHealthController {

    /**
     * Returns 200 with {@code "ok"} when the JVM can answer HTTP. Intentionally does not touch
     * Postgres, the cache, or any downstream so high-frequency probing is free.
     */
    @GetMapping(path = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Public liveness probe")
    public String healthz() {
        return "ok";
    }
}
