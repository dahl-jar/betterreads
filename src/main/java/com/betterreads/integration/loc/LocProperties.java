package com.betterreads.integration.loc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Library of Congress SRU config bound from {@code loc.*}.
 *
 * <p>{@code baseUrl} defaults to the direct {@code lx2.loc.gov:210} endpoint, which answers in ~0.4s
 * under a bulk walk. The {@code lccn.loc.gov/sru} proxy returns 502s under the same load, so it is a
 * fallback.
 *
 * @param baseUrl SRU endpoint, e.g. {@code http://lx2.loc.gov:210/lcdb}
 * @param connectTimeout TCP connect timeout in milliseconds
 * @param readTimeout per-response read timeout in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "loc")
public record LocProperties(
    @NotBlank String baseUrl,
    @Positive int connectTimeout,
    @Positive int readTimeout
) { }
