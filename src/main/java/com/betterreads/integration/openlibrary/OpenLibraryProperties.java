package com.betterreads.integration.openlibrary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OpenLibrary REST API config bound from {@code openlibrary.*}.
 *
 * <p>OpenLibrary is keyless, so there is no API key. {@code contactEmail} is required because
 * OpenLibrary throttles anonymous traffic and wants a contact in the {@code User-Agent};
 * {@code OpenLibraryWebClientConfig} puts it there.
 *
 * @param baseUrl OpenLibrary base, e.g. {@code https://openlibrary.org}
 * @param contactEmail contact address sent in the User-Agent so OpenLibrary can reach the operator
 * @param connectTimeout TCP connect timeout in milliseconds
 * @param readTimeout per-response read timeout in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "openlibrary")
public record OpenLibraryProperties(
    @NotBlank String baseUrl,
    @NotBlank String contactEmail,
    @Positive int connectTimeout,
    @Positive int readTimeout
) { }
