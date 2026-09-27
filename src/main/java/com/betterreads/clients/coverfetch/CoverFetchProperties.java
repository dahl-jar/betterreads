package com.betterreads.clients.coverfetch;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cover-download config bound from {@code cover-fetch.*}.
 *
 * @param connectTimeout in milliseconds
 * @param readTimeout in milliseconds
 * @param maxBytes largest cover body accepted, a larger download resolves to no cover
 */
@Validated
@ConfigurationProperties(prefix = "cover-fetch")
record CoverFetchProperties(
    @Positive int connectTimeout,
    @Positive int readTimeout,
    @Positive int maxBytes
) { }
