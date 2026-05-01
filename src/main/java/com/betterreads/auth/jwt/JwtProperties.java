package com.betterreads.auth.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT signing configuration. Bound from {@code jwt.*} keys in {@code application.yml}.
 *
 * <p>{@code secret} requires at least 32 UTF-8 bytes for HS256 — JJWT rejects shorter keys.
 * Validating at config bind time gives a clearer failure than the deeper JJWT exception.
 *
 * @param secret HS256 signing secret, at least 32 bytes UTF-8
 * @param issuer issuer claim written into every token
 * @param expirationMinutes token lifetime, must be positive
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    @NotBlank @Size(min = 32) String secret,
    @NotBlank String issuer,
    @Positive long expirationMinutes
) { }
