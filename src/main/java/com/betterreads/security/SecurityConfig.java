package com.betterreads.security;

import com.betterreads.ratelimit.RateLimitFilter;
import com.betterreads.web.RequestIdFilter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
final class SecurityConfig {

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    private static final String DOCS_CSP =
        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self' data:; frame-ancestors 'none'";

    private static final String ROBOTS_HEADER = "X-Robots-Tag";

    private static final String ROBOTS_NOINDEX = "noindex, nofollow";

    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

    private static final String[] LOCAL_SCRAPE_PATHS = {
        "/actuator/prometheus",
        "/actuator/health"
    };

    private static final String[] DOCS_PATHS = {
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    private static final String[] PUBLIC_CATALOG_GET_PATHS = {
        "/api/v1/search/**",
        "/api/v1/books/**",
        "/api/v1/authors/**",
        "/api/v1/reviews/**",
        "/api/v1/comments/**",
        "/api/v1/images/**"
    };

    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/resend-verification",
        "/healthz"
    };

    private final int managementPort;

    private final ObjectProvider<JwtDecoder> cloudflareAccessJwtDecoderProvider;

    SecurityConfig(
        @Value("${management.server.port}") final int managementPort,
        final ObjectProvider<JwtDecoder> cloudflareAccessJwtDecoderProvider
    ) {
        this.managementPort = managementPort;
        this.cloudflareAccessJwtDecoderProvider = cloudflareAccessJwtDecoderProvider;
    }

    @Bean
    @Order(0)
    SecurityFilterChain managementSecurityFilterChain(
        final HttpSecurity http,
        final RequestIdFilter requestIdFilter
    ) {
        final JwtDecoder decoder = cloudflareAccessJwtDecoderProvider.getIfAvailable();
        stateless(http, requestIdFilter)
            .securityMatcher(request ->
                request.getLocalPort() == managementPort
                    && EndpointRequest.toAnyEndpoint().matches(request));
        if (decoder != null) {
            http
                .oauth2ResourceServer(oauth2 -> oauth2
                    .bearerTokenResolver(new CloudflareAccessJwtAssertionResolver())
                    .jwt(jwt -> jwt.decoder(decoder)))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(LOCAL_SCRAPE_PATHS).permitAll()
                    .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain docsSecurityFilterChain(
        final HttpSecurity http,
        final RequestIdFilter requestIdFilter
    ) {
        stateless(http, requestIdFilter)
            .securityMatcher(DOCS_PATHS)
            .cors(Customizer.withDefaults())
            .headers(headers -> commonHeaders(headers)
                .contentSecurityPolicy(csp -> csp.policyDirectives(DOCS_CSP))
                .addHeaderWriter(new StaticHeadersWriter(ROBOTS_HEADER, ROBOTS_NOINDEX)))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(
        final HttpSecurity http,
        final JwtAuthenticationFilter jwtAuthenticationFilter,
        final RateLimitFilter rateLimitFilter,
        final RequestIdFilter requestIdFilter
    ) {
        stateless(http, requestIdFilter)
            .cors(Customizer.withDefaults())
            .headers(headers -> commonHeaders(headers)
                .contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_CATALOG_GET_PATHS).permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static HttpSecurity stateless(final HttpSecurity http, final RequestIdFilter requestIdFilter) {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(requestIdFilter, SecurityContextHolderFilter.class);
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

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
        final RateLimitFilter rateLimitFilter
    ) {
        final FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(
        final RequestIdFilter requestIdFilter
    ) {
        final FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(requestIdFilter);
        registration.setEnabled(false);
        return registration;
    }
}
