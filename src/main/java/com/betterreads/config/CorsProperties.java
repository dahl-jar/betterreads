package com.betterreads.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Allowed browser origins for cross-origin requests. Exact match, wildcards rejected at startup,
 * empty list closes CORS entirely.
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
