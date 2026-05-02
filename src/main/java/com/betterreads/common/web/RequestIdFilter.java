package com.betterreads.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a correlation ID on every request. If the caller sent {@code X-Request-Id} and the value
 * looks sane, that ID is reused; otherwise we generate a UUID. The ID lands in the SLF4J MDC
 * under {@code requestId} and goes back out in the response header, so a client tracing one
 * request through their logs sees the same ID we see in ours.
 *
 * <p>The validation regex {@code [A-Za-z0-9-]{1,64}} exists because the inbound header is
 * untrusted input that ends up in log lines. Without it, a CR/LF in the header would let a
 * caller forge log entries (CWE-117), and an oversized value would bloat the MDC for the rest
 * of the request.
 */
@Component
public final class RequestIdFilter extends OncePerRequestFilter {

    /** Inbound and outbound header name. {@code X-Request-Id} is what Cloudflare emits. */
    public static final String HEADER = "X-Request-Id";

    /** MDC key the logback pattern reads from. */
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;

    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{1," + MAX_LENGTH + "}$");

    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain filterChain
    ) throws ServletException, IOException {
        final String resolved = resolve(request.getHeader(HEADER));
        response.setHeader(HEADER, resolved);
        MDC.put(MDC_KEY, resolved);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(@Nullable final String inbound) {
        if (inbound != null && VALID_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
