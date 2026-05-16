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
 * Tags every request with an ID, reusing the inbound {@code X-Request-Id} when it is valid or
 * generating a UUID. The ID goes on the SLF4J MDC and is returned on the response header.
 *
 * <p>The format check blocks CR/LF log-forgery (CWE-117) and stops untrusted callers from
 * setting oversized MDC values.
 */
@Component
public final class RequestIdFilter extends OncePerRequestFilter {

    /** Inbound and outbound header name. */
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
