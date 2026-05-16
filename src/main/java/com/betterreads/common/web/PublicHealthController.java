package com.betterreads.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health check endpoint.
 *
 * <p>Returns 200 when the app is running. Detailed component health (database, mail) lives on
 * the actuator on the management port, which is not exposed publicly.
 */
@RestController
@Tag(name = "Operations", description = "Public probes used by external monitors")
public class PublicHealthController {

    /**
     * Returns 200 with {@code "ok"} when the app is running.
     *
     * <p>Does not touch Postgres, the cache, or any downstream so it can be called as often as
     * the monitor wants without affecting load.
     */
    @GetMapping(path = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Health check")
    public String healthz() {
        return "ok";
    }
}
