package com.betterreads.integration.image;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cover-download config bound from {@code cover-fetch.*}.
 *
 * @param connectTimeout TCP connect timeout in milliseconds
 * @param readTimeout per-response read timeout in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "cover-fetch")
public record CoverFetchProperties(
    @Positive int connectTimeout,
    @Positive int readTimeout
) { }
