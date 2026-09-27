package com.betterreads.web;

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
 * Tags each request with an id for the logs and the X-Request-Id response header.
 * an inbound id is reused only when it matches the format, so a caller can't forge log lines with
 * CR/LF (CWE-117) or push oversized MDC values
 */
@Component
public final class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /** MDC key the logback pattern reads from. */
    private static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;

    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{1," + MAX_LENGTH + "}$");

    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain filterChain
    ) throws ServletException, IOException {
        final String requestId = requestIdFrom(request.getHeader(HEADER));
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String requestIdFrom(@Nullable final String inbound) {
        if (inbound != null && VALID_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
