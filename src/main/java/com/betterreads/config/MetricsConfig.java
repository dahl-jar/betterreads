package com.betterreads.config;

import io.micrometer.core.instrument.config.MeterFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer filters for the HTTP request metric.
 *
 * <p>Kubernetes liveness, readiness, and startup probes hammer {@code /healthz} every few seconds,
 * which otherwise dominates {@code http_server_requests} and buries real traffic in the dashboards.
 * This drops the probe URI from that meter so request panels reflect user-facing traffic only;
 * liveness is still observable through the actuator health endpoint on the management port.
 */
@Configuration
public class MetricsConfig {

    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";

    private static final String URI_TAG = "uri";

    private static final String HEALTHZ_URI = "/healthz";

    @Bean
    MeterFilter excludeHealthzFromHttpRequests() {
        return MeterFilter.deny(id ->
            HTTP_SERVER_REQUESTS.equals(id.getName())
                && HEALTHZ_URI.equals(id.getTag(URI_TAG)));
    }
}
