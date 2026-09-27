package com.betterreads.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings bound from {@code jwt.*}. HS256 needs a 32-byte key, so a shorter secret fails at
 * startup.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    @NotBlank @Size(min = 32) String secret,
    @NotBlank String issuer,
    // TODO(once admins can ban users): drop to 15 minutes so a ban takes effect within 15 minutes
    @Positive long expirationMinutes,
    @Positive long refreshExpirationDays
) { }
