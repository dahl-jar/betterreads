package com.betterreads.security;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.betterreads.logging.LogSanitizer;
import com.betterreads.users.AccessTokenCheck;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates requests from the {@code Authorization: Bearer} token. An invalid token leaves the
 * request anonymous, so protected paths return 401.
 */
@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtIssuer jwtIssuer;

    private final AccessTokenCheck accessTokenCheck;

    JwtAuthenticationFilter(final JwtIssuer jwtIssuer, final AccessTokenCheck accessTokenCheck) {
        super();
        this.jwtIssuer = jwtIssuer;
        this.accessTokenCheck = accessTokenCheck;
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
            final AccessToken accessToken = jwtIssuer.parse(token);
            final long userId = accessToken.userId();
            if (!accessTokenCheck.isCurrent(userId, accessToken.credentialVersion())) {
                SecurityContextHolder.clearContext();
                LOG.warn("Rejected JWT with an old credential version or for a deleted account userId={}", userId);
                return;
            }
            final UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            LOG.debug("Authenticated request via JWT userId={}", userId);
        } catch (final InvalidJwtException ex) {
            SecurityContextHolder.clearContext();
            LOG.warn("Rejected JWT reason={}", LogSanitizer.forLog(ex.getMessage()));
        }
    }
}
