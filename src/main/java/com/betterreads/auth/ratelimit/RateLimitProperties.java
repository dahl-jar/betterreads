package com.betterreads.auth.ratelimit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-IP token-bucket limits for {@code /auth/login} and {@code /auth/register}. Empty bucket
 * returns 429.
 *
 * <p>{@code trustedProxies} CIDRs whitelist sources whose {@code X-Forwarded-For} header is
 * honored. Anything else is bucketed by {@code remoteAddr} so a direct attacker cannot pick a
 * fresh bucket per request by spoofing the header.
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
    @Positive long maxBuckets,
    @NotNull List<String> trustedProxies
) {

    public RateLimitProperties {
        trustedProxies = List.copyOf(trustedProxies);
    }
}
