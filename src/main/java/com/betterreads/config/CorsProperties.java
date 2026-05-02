package com.betterreads.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Allowed browser origins for cross-origin requests against this API.
 *
 * <p>Origins are matched exactly. {@code "*"} is rejected at startup because it would let any
 * site script the API on a logged-in user's behalf. An empty list means CORS is effectively off:
 * no browser origin is allowed, so the frontend has to be served from the same origin as the API
 * (which never happens in the deployed setup, so the empty default deliberately fails closed).
 *
 * @param allowedOrigins exact origin strings, e.g. {@code https://app.betterreads.example.com}
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
    @NotNull List<@Pattern(regexp = "^https?://[^*\\s]+$",
        message = "origin must be a scheme + host (no wildcards, no spaces)") String> allowedOrigins
) {

    public CorsProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
