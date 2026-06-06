package com.betterreads.auth.ratelimit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-IP token-bucket limits for the public auth and catalog-read endpoints, bound from
 * {@code auth.rate-limit.*}.
 *
 * <p>{@code X-Forwarded-For} is only read when the immediate client matches a CIDR in
 * {@code trustedProxies}. Otherwise the bucket key is {@code remoteAddr} so a direct attacker
 * cannot pick a fresh bucket by setting the header.
 */
@Validated
@ConfigurationProperties(prefix = "auth.rate-limit")
public record RateLimitProperties(
    @Positive long loginCapacity,
    @Positive long loginRefillTokens,
    @Positive long loginRefillSeconds,
    @Positive long registerCapacity,
    @Positive long registerRefillTokens,
    @Positive long registerRefillSeconds,
    @Positive long forgotPasswordCapacity,
    @Positive long forgotPasswordRefillTokens,
    @Positive long forgotPasswordRefillSeconds,
    @Positive long resetPasswordCapacity,
    @Positive long resetPasswordRefillTokens,
    @Positive long resetPasswordRefillSeconds,
    @Positive long verifyEmailCapacity,
    @Positive long verifyEmailRefillTokens,
    @Positive long verifyEmailRefillSeconds,
    @Positive long resendVerificationCapacity,
    @Positive long resendVerificationRefillTokens,
    @Positive long resendVerificationRefillSeconds,
    @Positive long searchCapacity,
    @Positive long searchRefillTokens,
    @Positive long searchRefillSeconds,
    @Positive long eventStreamCapacity,
    @Positive long eventStreamRefillTokens,
    @Positive long eventStreamRefillSeconds,
    @Positive long bucketTtlSeconds,
    @NotNull List<String> trustedProxies
) {

    public RateLimitProperties {
        trustedProxies = List.copyOf(trustedProxies);
    }
}
