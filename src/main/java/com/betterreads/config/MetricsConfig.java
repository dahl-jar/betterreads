package com.betterreads.config;

import io.micrometer.core.instrument.config.MeterFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer filters for the HTTP request metric.
 *
 * <p>Health probes call {@code /healthz} every few seconds, which would dominate
 * {@code http_server_requests} and hide real traffic, so that URI is dropped from the meter.
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
