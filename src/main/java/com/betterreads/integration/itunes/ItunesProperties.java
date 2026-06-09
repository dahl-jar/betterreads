package com.betterreads.integration.itunes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Apple Books (iTunes Search API) config bound from {@code itunes.*}.
 *
 * @param baseUrl host serving {@code /search}, e.g. {@code https://itunes.apple.com}
 * @param connectTimeout TCP connect timeout in milliseconds
 * @param readTimeout per-response read timeout in milliseconds
 * @param ratePerMinute the request budget per minute, kept well under the unauthenticated cap so a
 *     backfill drains then resumes without tripping the real limit
 */
@Validated
@ConfigurationProperties(prefix = "itunes")
public record ItunesProperties(
    @NotBlank String baseUrl,
    @Positive int connectTimeout,
    @Positive int readTimeout,
    @Positive int ratePerMinute
) { }
