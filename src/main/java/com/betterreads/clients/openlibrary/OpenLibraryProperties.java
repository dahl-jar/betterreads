package com.betterreads.clients.openlibrary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** OpenLibrary settings bound from {@code openlibrary.*}. Timeouts are in milliseconds. */
@Validated
@ConfigurationProperties(prefix = "openlibrary")
record OpenLibraryProperties(
    @NotBlank String baseUrl,
    @NotBlank String contactEmail,
    @Positive int connectTimeout,
    @Positive int readTimeout
) { }
