package com.betterreads.integration.googlebooks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Google Books REST API config bound from {@code googlebooks.*}.
 *
 * <p>{@code apiKey} is not {@code @NotBlank} so the app boots without a key in profiles
 * that never call Google Books; {@code GoogleBooksWebClientConfig} fails fast on the first
 * request if the key is missing.
 *
 * @param baseUrl Books API base, e.g. {@code https://www.googleapis.com/books/v1}
 * @param apiKey Google Cloud API key with the Books API enabled
 * @param connectTimeout TCP connect timeout in milliseconds
 * @param readTimeout per-response read timeout in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "googlebooks")
public record GoogleBooksProperties(
    @NotBlank String baseUrl,
    String apiKey,
    @Positive int connectTimeout,
    @Positive int readTimeout
) {

    /**
     * Trims the key so a secret stored with a trailing newline does not reach the request as
     * {@code key=...\n}, which the URI builder rejects on every Google Books call.
     */
    public GoogleBooksProperties {
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }
    }
}
