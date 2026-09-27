package com.betterreads.clients.loc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Library of Congress SRU settings.
 *
 * <p>{@code lccn.loc.gov/sru} returns 502s under a bulk walk, so {@code base-url} defaults to the lx2 host.
 *
 * @param connectTimeout milliseconds
 * @param readTimeout milliseconds per response
 */
@Validated
@ConfigurationProperties(prefix = "loc")
record LocProperties(
    @NotBlank String baseUrl,
    @Positive int connectTimeout,
    @Positive int readTimeout
) { }
