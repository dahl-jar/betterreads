package com.betterreads.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hosts the public liveness endpoint that Cloudflare and other external monitors poll.
 */
@RestController
@Tag(name = "Operations", description = "Public probes used by external monitors")
public class PublicHealthController {

    /**
     * Returns 200 with body {@code "ok"} if the process can answer HTTP. The endpoint is
     * intentionally trivial: it does not touch Postgres, the cache, or any downstream
     * service.
     *
     * <p>A monitor that hits this every 30 seconds would otherwise add real load to the
     * database for no diagnostic gain; if Postgres is down, real API calls already return
     * 5xx and the actuator health endpoint on the management port reports component status.
     * Make this richer only if a CDN probe genuinely needs more than "is the JVM up."
     */
    @GetMapping(path = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Public liveness probe")
    public String healthz() {
        return "ok";
    }
}
