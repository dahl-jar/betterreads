package com.betterreads.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import com.betterreads.common.util.LogSanitizer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer} tokens and populates the security context. Invalid tokens
 * leave the context empty so the downstream entry point returns 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtIssuer jwtIssuer;

    public JwtAuthenticationFilter(final JwtIssuer jwtIssuer) {
        super();
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain filterChain
    ) throws ServletException, IOException {
        final String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token != null) {
            authenticate(token, request);
        }
        filterChain.doFilter(request, response);
    }

    @Nullable
    private static String extractBearerToken(@Nullable final String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    private void authenticate(final String token, final HttpServletRequest request) {
        try {
            final long userId = jwtIssuer.parseUserId(token);
            final UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            LOG.debug("auth.jwt.success userId={}", userId);
        } catch (final InvalidJwtException ex) {
            SecurityContextHolder.clearContext();
            LOG.warn("auth.jwt.invalid reason={}", LogSanitizer.forLog(ex.getMessage()));
        }
    }
}
