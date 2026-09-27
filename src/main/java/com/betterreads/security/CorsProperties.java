package com.betterreads.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Browser origins allowed to call the API, each a full origin such as
 * {@code https://app.betterreads.example.com}. A wildcard fails at startup and an empty list
 * blocks every cross-origin call.
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
record CorsProperties(
    @NotNull List<@Pattern(regexp = "^https?://[^*\\s]+$",
        message = "origin must be a scheme + host (no wildcards, no spaces)") String> allowedOrigins
) {

    public CorsProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
