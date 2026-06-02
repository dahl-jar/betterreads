package com.betterreads.integration.wikidata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Wikidata config bound from {@code wikidata.*}.
 *
 * @param baseUrl host serving {@code /w/api.php} and {@code /wiki/Special:EntityData}, e.g.
 *     {@code https://www.wikidata.org}
 * @param connectTimeout TCP connect timeout in milliseconds
 * @param readTimeout per-response read timeout in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "wikidata")
public record WikidataProperties(
    @NotBlank String baseUrl,
    @Positive int connectTimeout,
    @Positive int readTimeout
) { }
