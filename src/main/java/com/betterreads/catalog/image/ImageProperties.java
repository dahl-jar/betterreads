package com.betterreads.catalog.image;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Image-serving config bound from {@code images.*}.
 *
 * @param publicBaseUrl the public origin of this API, prepended to served cover URLs so a client on
 *     a different origin resolves them to this API
 */
@Validated
@ConfigurationProperties(prefix = "images")
public record ImageProperties(@NotBlank String publicBaseUrl) {
}
