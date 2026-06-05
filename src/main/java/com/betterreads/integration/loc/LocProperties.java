package com.betterreads.integration.loc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Library of Congress SRU config bound from {@code loc.*}.
 *
 * <p>{@code baseUrl} defaults to the direct {@code http://lx2.loc.gov:210/lcdb} endpoint, which
 * answers in ~0.4s under a bulk walk; the {@code https://lccn.loc.gov/sru/lcdb} proxy returns 502s
 * under the same load and serves as a documented fallback only. The {@code :210} endpoint needs an
 * outbound tcp/210 egress rule on the cluster before the discovery job runs.
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
