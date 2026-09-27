package com.betterreads.clients.googlebooks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Google Books REST API config bound from {@code googlebooks.*}.
 *
 * <p>{@code apiKey} may be blank so the app boots in profiles that never call Google Books.
 * Without a key, requests go out on Google's keyless quota.
 *
 * @param baseUrl Books API base, e.g. {@code https://www.googleapis.com/books/v1}
 * @param apiKey Google Cloud API key with the Books API enabled
 * @param connectTimeout TCP connect timeout in milliseconds
 * @param readTimeout per-response read timeout in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "googlebooks")
record GoogleBooksProperties(
    @NotBlank String baseUrl,
    @Nullable String apiKey,
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
