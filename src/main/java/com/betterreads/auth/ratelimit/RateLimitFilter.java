package com.betterreads.auth.ratelimit;

import com.betterreads.common.util.LogSanitizer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket rate limiter for the public auth endpoints (login, register, forgot-password,
 * reset-password, verify-email, resend-verification). Each path keeps its own per-IP bucket
 * cache and bandwidth recipe so a burst against one endpoint does not consume another's budget.
 * Buckets are keyed by client IP; trusted-proxy CIDRs unlock {@code X-Forwarded-For} parsing
 * so direct callers cannot spoof the limit. Empty bucket returns 429 with {@code Retry-After}.
 */
@Component
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private static final String FORGOT_PASSWORD_PATH = "/api/v1/auth/forgot-password";

    private static final String RESET_PASSWORD_PATH = "/api/v1/auth/reset-password";

    private static final String VERIFY_EMAIL_PATH = "/api/v1/auth/verify-email";

    private static final String RESEND_VERIFICATION_PATH = "/api/v1/auth/resend-verification";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private static final String CF_CONNECTING_IP_HEADER = "CF-Connecting-IP";

    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private static final Duration BUCKET_TTL = Duration.ofMinutes(15);

    private final Map<String, Endpoint> endpoints;

    private final List<CidrRange> trustedProxies;

    /**
     * Trusted-proxy CIDRs are parsed once at construction. Malformed entries are dropped with
     * a warning log so a single bad config line cannot break filter startup.
     */
    // TODO(when scaling beyond one app instance): move buckets to Redis so replicas share limits
    public RateLimitFilter(final RateLimitProperties properties) {
        super();
        this.endpoints = buildEndpoints(properties);
        this.trustedProxies = parseCidrs(properties.trustedProxies());
    }

    private static Map<String, Endpoint> buildEndpoints(final RateLimitProperties props) {
        return Map.ofEntries(
            Map.entry(LOGIN_PATH, endpoint(props.maxBuckets(), props.loginCapacity(),
                props.loginRefillTokens(), props.loginRefillSeconds())),
            Map.entry(REGISTER_PATH, endpoint(props.maxBuckets(), props.registerCapacity(),
                props.registerRefillTokens(), props.registerRefillSeconds())),
            Map.entry(FORGOT_PASSWORD_PATH, endpoint(props.maxBuckets(), props.forgotPasswordCapacity(),
                props.forgotPasswordRefillTokens(), props.forgotPasswordRefillSeconds())),
            Map.entry(RESET_PASSWORD_PATH, endpoint(props.maxBuckets(), props.resetPasswordCapacity(),
                props.resetPasswordRefillTokens(), props.resetPasswordRefillSeconds())),
            Map.entry(VERIFY_EMAIL_PATH, endpoint(props.maxBuckets(), props.verifyEmailCapacity(),
                props.verifyEmailRefillTokens(), props.verifyEmailRefillSeconds())),
            Map.entry(RESEND_VERIFICATION_PATH, endpoint(props.maxBuckets(), props.resendVerificationCapacity(),
                props.resendVerificationRefillTokens(), props.resendVerificationRefillSeconds()))
        );
    }

    private static Endpoint endpoint(
        final long maxBuckets,
        final long capacity,
        final long refillTokens,
        final long refillSeconds
    ) {
        final Cache<String, Bucket> cache = Caffeine.newBuilder()
            .expireAfterAccess(BUCKET_TTL)
            .maximumSize(maxBuckets)
            .build();
        final Supplier<Bandwidth> bandwidth = () -> Bandwidth.builder()
            .capacity(capacity)
            .refillGreedy(refillTokens, Duration.ofSeconds(refillSeconds))
            .build();
        return new Endpoint(cache, bandwidth);
    }

    /**
     * Drops all in-memory buckets. Used by tests to isolate state between scenarios.
     */
    public void reset() {
        endpoints.values().forEach(e -> e.cache().invalidateAll());
    }

    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain filterChain
    ) throws ServletException, IOException {
        final Bucket bucket = bucketFor(request);
        if (bucket == null) {
            filterChain.doFilter(request, response);
            return;
        }
        final ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }
        final long retryAfterSeconds = ceilSeconds(probe.getNanosToWaitForRefill());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(RETRY_AFTER_HEADER, Long.toString(retryAfterSeconds));
        LOG.warn("Rate limit hit, returning 429 path={} ip={} retryAfter={}s",
            LogSanitizer.forLog(request.getRequestURI()),
            LogSanitizer.forLog(clientIp(request)),
            retryAfterSeconds);
    }

    @Nullable
    private Bucket bucketFor(final HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        final Endpoint endpoint = endpoints.get(request.getRequestURI());
        if (endpoint == null) {
            return null;
        }
        return endpoint.cache().get(clientIp(request),
            ip -> Bucket.builder().addLimit(endpoint.bandwidth().get()).build());
    }

    /**
     * Pairs a per-IP bucket cache with the bandwidth recipe used to build new buckets. Each
     * rate-limited path owns one {@code Endpoint}.
     */
    private record Endpoint(Cache<String, Bucket> cache, Supplier<Bandwidth> bandwidth) { }

    /**
     * Resolves the rate-limit bucket key from the incoming request.
     *
     * <p>{@code CF-Connecting-IP} is preferred when present: Cloudflare sets it from the real
     * client connection and overwrites any client-supplied value, so it is unforgeable behind
     * the tunnel. Trusting this header closes the bypass where a fresh forged
     * {@code X-Forwarded-For} per request granted a fresh bucket per request after Cloudflare
     * appended the real IP to the chain.
     *
     * <p>The legacy trusted-proxy-gated {@code X-Forwarded-For} path stays in place as a
     * fallback for non-Cloudflare environments such as local dev and integration tests that
     * exercise the proxy CIDR logic directly.
     */
    private String clientIp(final HttpServletRequest request) {
        final String cfConnectingIp = request.getHeader(CF_CONNECTING_IP_HEADER);
        if (cfConnectingIp != null && parseAddress(cfConnectingIp.trim()) != null) {
            return cfConnectingIp.trim();
        }
        final String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        final String forwarded = firstForwardedIp(request.getHeader(FORWARDED_FOR_HEADER));
        return forwarded != null ? forwarded : remoteAddr;
    }

    private boolean isTrustedProxy(@Nullable final String ipAddress) {
        if (ipAddress == null || trustedProxies.isEmpty()) {
            return false;
        }
        final InetAddress address = parseAddress(ipAddress);
        if (address == null) {
            return false;
        }
        for (final CidrRange range : trustedProxies) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String firstForwardedIp(@Nullable final String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        final int comma = header.indexOf(',');
        final String first = (comma < 0 ? header : header.substring(0, comma)).trim();
        return parseAddress(first) != null ? first : null;
    }

    @Nullable
    private static InetAddress parseAddress(final String value) {
        try {
            return InetAddress.getByName(value);
        } catch (final UnknownHostException ex) {
            return null;
        }
    }

    private static List<CidrRange> parseCidrs(final List<String> raw) {
        final List<CidrRange> out = new ArrayList<>(raw.size());
        for (final String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            final CidrRange range = CidrRange.parse(entry);
            if (range != null) {
                out.add(range);
            } else {
                LOG.warn("Skipped malformed trusted-proxy CIDR cidr={}", LogSanitizer.forLog(entry));
            }
        }
        return List.copyOf(out);
    }

    private static long ceilSeconds(final long nanos) {
        final long perSecond = TimeUnit.SECONDS.toNanos(1);
        final long ceil = (nanos + perSecond - 1) / perSecond;
        return Math.max(ceil, 1L);
    }
}
