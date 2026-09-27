package com.betterreads.ratelimit;

import com.betterreads.logging.LogSanitizer;

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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP token-bucket rate limiter for the public auth, search, and catalog endpoints, shared
 * across replicas through Redis.
 *
 * <p>Each path has its own per-IP bucket so a burst against one endpoint does not eat
 * another's budget. An empty bucket returns 429 with {@code Retry-After}.
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

    private static final List<String> PUBLIC_READ_PREFIXES = List.of(
        BOOK_DETAIL_PREFIX, "/api/v1/reviews/", "/api/v1/comments/", "/api/v1/images/");

    private static final String EVENT_STREAM_SUFFIX = "/events";

    private static final String COMMENTS_SUFFIX = "/comments";

    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final Map<String, Endpoint> endpoints;

    private final Endpoint publicReadEndpoint;

    private final Endpoint eventStreamEndpoint;

    private final Endpoint commentWriteEndpoint;

    private final ClientIpResolver clientIpResolver;

    private final ProxyManager<String> proxyManager;

    private final StatefulRedisConnection<String, byte[]> redis;

    public RateLimitFilter(
        final RateLimitProperties properties,
        final ProxyManager<String> proxyManager,
        final StatefulRedisConnection<String, byte[]> redis
    ) {
        super();
        this.endpoints = buildEndpoints(properties);
        this.publicReadEndpoint = endpoint(HttpMethod.GET, "book-detail", properties.searchCapacity(),
            properties.searchRefillTokens(), properties.searchRefillSeconds());
        this.eventStreamEndpoint = endpoint(HttpMethod.GET, "event-stream",
            properties.eventStreamCapacity(),
            properties.eventStreamRefillTokens(), properties.eventStreamRefillSeconds());
        this.commentWriteEndpoint = endpoint(HttpMethod.POST, "comment-write",
            properties.searchCapacity(),
            properties.searchRefillTokens(), properties.searchRefillSeconds());
        this.clientIpResolver = new ClientIpResolver(properties.trustedProxies());
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
        final BucketConfiguration config = DistributedRateLimiter.greedyBucket(
            capacity, refillTokens, Duration.ofSeconds(refillSeconds));
        return new Endpoint(method, keyPrefix, config);
    }

    /** Deletes every rate-limit bucket from Redis, so the next request starts at full. For tests. */
    public void reset() {
        Stream.concat(
                endpoints.values().stream(),
                Stream.of(publicReadEndpoint, eventStreamEndpoint, commentWriteEndpoint))
            .map(Endpoint::keyPrefix)
            .forEach(prefix -> {
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
        final Endpoint endpoint = endpointFor(request);
        if (endpoint == null) {
            filterChain.doFilter(request, response);
            return;
        }
        final String clientIp = clientIpResolver.clientIp(request);
        final String key = endpoint.keyPrefix() + ':' + clientIp;
        final Bucket bucket = proxyManager.getProxy(key, endpoint::config);
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
            LogSanitizer.forLog(clientIp),
            retryAfterSeconds);
    }

    @Nullable
    private Endpoint endpointFor(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        final Endpoint exact = endpoints.get(uri);
        if (exact != null && exact.method().matches(request.getMethod())) {
            return exact;
        }
        if (HttpMethod.POST.matches(request.getMethod())) {
            return uri.endsWith(COMMENTS_SUFFIX) ? commentWriteEndpoint : null;
        }
        return HttpMethod.GET.matches(request.getMethod()) ? readEndpointFor(uri) : null;
    }

    @Nullable
    private Endpoint readEndpointFor(final String uri) {
        if (uri.endsWith(EVENT_STREAM_SUFFIX)) {
            return eventStreamEndpoint;
        }
        final boolean publicRead = PUBLIC_READ_PREFIXES.stream().anyMatch(uri::startsWith);
        return publicRead ? publicReadEndpoint : null;
    }

    private record Endpoint(HttpMethod method, String keyPrefix, BucketConfiguration config) { }

    private static long ceilSeconds(final long nanos) {
        final long perSecond = TimeUnit.SECONDS.toNanos(1);
        final long ceil = (nanos + perSecond - 1) / perSecond;
        return Math.max(ceil, 1L);
    }
}
