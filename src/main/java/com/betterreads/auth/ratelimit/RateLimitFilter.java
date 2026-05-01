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
 * Token-bucket rate limiter for {@code POST /auth/login} and {@code POST /auth/register}.
 *
 * <p>Buckets are keyed by client IP. The IP comes from the direct {@code remoteAddr} unless
 * the request arrived via a configured trusted proxy CIDR, in which case the first valid IP
 * from {@code X-Forwarded-For} is used. This prevents a direct attacker from bypassing the
 * limit by spoofing the header. Empty bucket returns {@code 429} with {@code Retry-After}.
 */
@Component
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private static final Duration BUCKET_TTL = Duration.ofMinutes(15);

    private final Cache<String, Bucket> loginBuckets;

    private final Cache<String, Bucket> registerBuckets;

    private final Supplier<Bandwidth> loginBandwidth;

    private final Supplier<Bandwidth> registerBandwidth;

    private final List<CidrRange> trustedProxies;

    public RateLimitFilter(final RateLimitProperties properties) {
        super();
        this.loginBandwidth = () -> Bandwidth.builder()
            .capacity(properties.loginCapacity())
            .refillGreedy(properties.loginRefillTokens(), Duration.ofSeconds(properties.loginRefillSeconds()))
            .build();
        this.registerBandwidth = () -> Bandwidth.builder()
            .capacity(properties.registerCapacity())
            .refillGreedy(properties.registerRefillTokens(), Duration.ofSeconds(properties.registerRefillSeconds()))
            .build();
        this.loginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(BUCKET_TTL)
            .maximumSize(properties.maxBuckets())
            .build();
        this.registerBuckets = Caffeine.newBuilder()
            .expireAfterAccess(BUCKET_TTL)
            .maximumSize(properties.maxBuckets())
            .build();
        this.trustedProxies = parseCidrs(properties.trustedProxies());
    }

    /**
     * Drops all in-memory buckets. Used by tests to isolate rate-limit state between scenarios.
     */
    public void reset() {
        loginBuckets.invalidateAll();
        registerBuckets.invalidateAll();
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
        LOG.warn("auth.ratelimit.blocked path={} ip={} retryAfter={}s",
            LogSanitizer.forLog(request.getRequestURI()),
            LogSanitizer.forLog(clientIp(request)),
            retryAfterSeconds);
    }

    @Nullable
    private Bucket bucketFor(final HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        final String path = request.getRequestURI();
        final String key = clientIp(request);
        if (LOGIN_PATH.equals(path)) {
            return loginBuckets.get(key, ip -> Bucket.builder().addLimit(loginBandwidth.get()).build());
        }
        if (REGISTER_PATH.equals(path)) {
            return registerBuckets.get(key, ip -> Bucket.builder().addLimit(registerBandwidth.get()).build());
        }
        return null;
    }

    private String clientIp(final HttpServletRequest request) {
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
                LOG.warn("auth.ratelimit.invalid-trusted-proxy cidr={}", LogSanitizer.forLog(entry));
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
