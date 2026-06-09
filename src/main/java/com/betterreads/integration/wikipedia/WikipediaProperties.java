package com.betterreads.integration.wikipedia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Wikipedia config bound from {@code wikipedia.*}.
 *
 * @param baseUrl host serving {@code /api/rest_v1/page/summary/}, e.g. {@code https://en.wikipedia.org}
 * @param connectTimeout TCP connect timeout in milliseconds
 * @param readTimeout per-response read timeout in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "wikipedia")
public record WikipediaProperties(
    @NotBlank String baseUrl,
    @Positive int connectTimeout,
    @Positive int readTimeout
) { }
