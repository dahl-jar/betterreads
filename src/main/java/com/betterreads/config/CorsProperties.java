package com.betterreads.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Allowed browser origins for cross-origin requests.
 *
 * <p>Each entry must be a full origin, e.g. {@code https://app.betterreads.example.com}.
 * Patterns like {@code *.example.com} are rejected at startup. An empty list closes CORS.
 *
 * @param allowedOrigins allowed origins
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
