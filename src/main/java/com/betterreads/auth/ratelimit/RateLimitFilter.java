package com.betterreads.auth.ratelimit;

import com.betterreads.common.util.LogSanitizer;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;

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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket rate limiter for the public auth endpoints, backed by Redis so every app replica
 * shares one count per client.
 *
 * <p>Each path has its own per-IP bucket so a burst against one endpoint does not eat
 * another's budget. {@code X-Forwarded-For} is only read from trusted-proxy CIDRs so a
 * direct caller cannot spoof the IP. An empty bucket returns 429 with {@code Retry-After}.
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

    private static final String SEARCH_PATH = "/api/v1/search/books";

    private static final String BOOK_DETAIL_PREFIX = "/api/v1/books/";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private static final String CF_CONNECTING_IP_HEADER = "CF-Connecting-IP";

    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final Map<String, Endpoint> endpoints;

    private final Endpoint detailEndpoint;

    private final List<CidrRange> trustedProxies;

    private final ProxyManager<String> proxyManager;

    private final StatefulRedisConnection<String, byte[]> redis;

    /**
     * Parses the trusted-proxy CIDRs once at construction.
     *
     * <p>Malformed entries are logged and dropped so one bad config line cannot break startup.
     */
    public RateLimitFilter(
        final RateLimitProperties properties,
        final ProxyManager<String> proxyManager,
        final StatefulRedisConnection<String, byte[]> redis
    ) {
        super();
        this.endpoints = buildEndpoints(properties);
        this.detailEndpoint = endpoint(HttpMethod.GET, "book-detail", properties.searchCapacity(),
            properties.searchRefillTokens(), properties.searchRefillSeconds());
        this.trustedProxies = parseCidrs(properties.trustedProxies());
        this.proxyManager = proxyManager;
        this.redis = redis;
    }

    private static Map<String, Endpoint> buildEndpoints(final RateLimitProperties props) {
        return Map.ofEntries(
            Map.entry(LOGIN_PATH, endpoint(HttpMethod.POST, "login", props.loginCapacity(),
                props.loginRefillTokens(), props.loginRefillSeconds())),
            Map.entry(REGISTER_PATH, endpoint(HttpMethod.POST, "register", props.registerCapacity(),
                props.registerRefillTokens(), props.registerRefillSeconds())),
            Map.entry(FORGOT_PASSWORD_PATH, endpoint(HttpMethod.POST, "forgot-password",
                props.forgotPasswordCapacity(),
                props.forgotPasswordRefillTokens(), props.forgotPasswordRefillSeconds())),
            Map.entry(RESET_PASSWORD_PATH, endpoint(HttpMethod.POST, "reset-password",
                props.resetPasswordCapacity(),
                props.resetPasswordRefillTokens(), props.resetPasswordRefillSeconds())),
            Map.entry(VERIFY_EMAIL_PATH, endpoint(HttpMethod.POST, "verify-email",
                props.verifyEmailCapacity(),
                props.verifyEmailRefillTokens(), props.verifyEmailRefillSeconds())),
            Map.entry(RESEND_VERIFICATION_PATH, endpoint(HttpMethod.POST, "resend-verification",
                props.resendVerificationCapacity(),
                props.resendVerificationRefillTokens(), props.resendVerificationRefillSeconds())),
            Map.entry(SEARCH_PATH, endpoint(HttpMethod.GET, "search", props.searchCapacity(),
                props.searchRefillTokens(), props.searchRefillSeconds()))
        );
    }

    private static Endpoint endpoint(
        final HttpMethod method,
        final String keyPrefix,
        final long capacity,
        final long refillTokens,
        final long refillSeconds
    ) {
        final Bandwidth bandwidth = Bandwidth.builder()
            .capacity(capacity)
            .refillGreedy(refillTokens, Duration.ofSeconds(refillSeconds))
            .build();
        final BucketConfiguration config = BucketConfiguration.builder().addLimit(bandwidth).build();
        return new Endpoint(method, keyPrefix, config);
    }

    /** Deletes every rate-limit bucket from Redis, so the next request starts at full. For tests. */
    public void reset() {
        final List<String> prefixes = new ArrayList<>();
        endpoints.values().forEach(e -> prefixes.add(e.keyPrefix()));
        prefixes.add(detailEndpoint.keyPrefix());
        prefixes.forEach(prefix -> {
            final List<String> keys = redis.sync().keys(prefix + ":*");
            if (!keys.isEmpty()) {
                redis.sync().del(keys.toArray(new String[0]));
            }
        });
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
        final Endpoint endpoint = endpointFor(request);
        if (endpoint == null) {
            return null;
        }
        final String key = endpoint.keyPrefix() + ':' + clientIp(request);
        return proxyManager.getProxy(key, endpoint::config);
    }

    @Nullable
    private Endpoint endpointFor(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        final Endpoint exact = endpoints.get(uri);
        if (exact != null && exact.method().matches(request.getMethod())) {
            return exact;
        }
        if (detailEndpoint.method().matches(request.getMethod())
            && uri.startsWith(BOOK_DETAIL_PREFIX)) {
            return detailEndpoint;
        }
        return null;
    }

    /** The HTTP method and Redis key prefix that isolate one endpoint's buckets, plus its recipe. */
    private record Endpoint(HttpMethod method, String keyPrefix, BucketConfiguration config) { }

    /**
     * Resolves the bucket key from the incoming request.
     *
     * <p>Prefers {@code CF-Connecting-IP} because Cloudflare overwrites any client-supplied
     * value, making it unforgeable behind the tunnel. Falls back to trusted-proxy-gated
     * {@code X-Forwarded-For} for local dev and tests.
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
