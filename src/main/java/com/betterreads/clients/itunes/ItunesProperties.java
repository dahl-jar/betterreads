package com.betterreads.clients.itunes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Apple Books (iTunes Search API) config bound from {@code itunes.*}.
 *
 * @param baseUrl host serving {@code /search}, e.g. {@code https://itunes.apple.com}
 * @param connectTimeout in milliseconds
 * @param readTimeout in milliseconds
 * @param ratePerMinute stays under Apple's unauthenticated cap
 */
@Validated
@ConfigurationProperties(prefix = "itunes")
public record ItunesProperties(
    @NotBlank String baseUrl,
    @Positive int connectTimeout,
    @Positive int readTimeout,
    @Positive int ratePerMinute
) { }
