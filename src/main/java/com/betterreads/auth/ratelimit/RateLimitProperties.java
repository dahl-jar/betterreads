package com.betterreads.auth.ratelimit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-IP rate limits for the public auth endpoints.
 *
 * <p>Token bucket: a client gets {@code capacity} tokens, refilled at {@code refillTokens} per
 * {@code refillSeconds}. One request consumes one token; an empty bucket returns {@code 429}.
 *
 * <p>{@code trustedProxies} lists CIDR ranges whose {@code X-Forwarded-For} header is honored.
 * Requests from any other source are bucketed by their direct {@code remoteAddr} so an attacker
 * with direct access to the origin can't pick a fresh bucket per request by spoofing the header.
 * Leave empty to ignore the header entirely.
 *
 * @param loginCapacity max burst for {@code /auth/login}
 * @param loginRefillTokens tokens added per refill window for login
 * @param loginRefillSeconds refill window for login, in seconds
 * @param registerCapacity max burst for {@code /auth/register}
 * @param registerRefillTokens tokens added per refill window for register
 * @param registerRefillSeconds refill window for register, in seconds
 * @param maxBuckets per-endpoint cap on tracked clients; oldest entries are evicted under pressure
 * @param trustedProxies CIDR ranges whose forwarded-for header is honored
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
