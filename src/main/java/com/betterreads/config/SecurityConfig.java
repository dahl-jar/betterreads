package com.betterreads.config;

import com.betterreads.auth.jwt.JwtAuthenticationFilter;
import com.betterreads.auth.ratelimit.RateLimitFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Two security filter chains. The first matches docs paths and uses a relaxed CSP that lets
 * Swagger UI's inline scripts and styles run. The second matches everything else and uses a
 * locked-down CSP suitable for a JSON API.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    private static final String DOCS_CSP =
        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self' data:; frame-ancestors 'none'";

    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

    private static final String[] DOCS_PATHS = {
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/register",
        "/api/v1/auth/login"
    };

    @Bean
    @Order(1)
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    SecurityFilterChain docsSecurityFilterChain(final HttpSecurity http) throws Exception {
        http
            .securityMatcher(DOCS_PATHS)
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> commonHeaders(headers)
                .contentSecurityPolicy(csp -> csp.policyDirectives(DOCS_CSP)))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    SecurityFilterChain apiSecurityFilterChain(
        final HttpSecurity http,
        final JwtAuthenticationFilter jwtAuthenticationFilter,
        final RateLimitFilter rateLimitFilter
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> commonHeaders(headers)
                .contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static HeadersConfigurer<HttpSecurity> commonHeaders(final HeadersConfigurer<HttpSecurity> headers) {
        return headers
            .contentTypeOptions(opts -> { })
            .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                .requestMatcher(req -> true))
            .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            .permissionsPolicyHeader(pp -> pp.policy(PERMISSIONS_POLICY));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
