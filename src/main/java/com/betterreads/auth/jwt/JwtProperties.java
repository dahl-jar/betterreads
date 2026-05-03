package com.betterreads.auth.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT signing configuration bound from {@code jwt.*}. The 32-byte minimum on {@code secret}
 * matches HS256's lower bound; validating at bind time fails earlier than JJWT does.
 *
 * @param secret HS256 signing secret, at least 32 bytes UTF-8
 * @param issuer issuer claim written into every token
 * @param expirationMinutes access token lifetime in minutes
 * @param refreshExpirationDays refresh token lifetime in days
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    @NotBlank @Size(min = 32) String secret,
    @NotBlank String issuer,
    @Positive long expirationMinutes,
    @Positive long refreshExpirationDays
) { }
