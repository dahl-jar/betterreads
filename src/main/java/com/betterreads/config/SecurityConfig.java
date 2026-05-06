package com.betterreads.config;

import com.betterreads.auth.jwt.JwtAuthenticationFilter;
import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.common.web.RequestIdFilter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

/**
 * Three filter chains, ordered most-specific first: management (actuator on the internal port),
 * docs (Swagger UI with relaxed CSP), and api (JWT, rate limit, strict CSP).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public final class SecurityConfig {

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    private static final String DOCS_CSP =
        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self' data:; frame-ancestors 'none'";

    private static final String ROBOTS_HEADER = "X-Robots-Tag";

    private static final String ROBOTS_NOINDEX = "noindex, nofollow";

    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

    private static final String[] DOCS_PATHS = {
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/healthz"
    };

    private final int managementPort;

    private final ObjectProvider<JwtDecoder> cloudflareAccessJwtDecoderProvider;

    /**
     * The Cloudflare Access JWT decoder is optional. When absent, the management chain stays
     * at {@code permitAll}. Resolved lazily inside the chain bean to avoid bean-creation order
     * issues.
     */
    @Autowired
    public SecurityConfig(
        @Value("${management.server.port}") final int managementPort,
        final ObjectProvider<JwtDecoder> cloudflareAccessJwtDecoderProvider
    ) {
        this.managementPort = managementPort;
        this.cloudflareAccessJwtDecoderProvider = cloudflareAccessJwtDecoderProvider;
    }

    /**
     * Matches actuator paths only when the request arrives on the management port. With a
     * Cloudflare Access JWT decoder configured, requires a valid {@code Cf-Access-Jwt-Assertion}
     * header. Without one, falls back to {@code permitAll}.
     */
    @Bean
    @Order(0)
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    SecurityFilterChain managementSecurityFilterChain(
        final HttpSecurity http,
        final RequestIdFilter requestIdFilter
    ) throws Exception {
        final JwtDecoder decoder = cloudflareAccessJwtDecoderProvider.getIfAvailable();
        http
            .securityMatcher(request ->
                request.getLocalPort() == managementPort
                    && EndpointRequest.toAnyEndpoint().matches(request))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(requestIdFilter, SecurityContextHolderFilter.class);
        if (decoder != null) {
            http
                .oauth2ResourceServer(oauth2 -> oauth2
                    .bearerTokenResolver(new CloudflareAccessJwtAssertionResolver())
                    .jwt(jwt -> jwt.decoder(decoder)))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }

    /**
     * Matches Swagger UI and OpenAPI doc paths. Relaxes CSP enough for the bundled UI to load
     * its own scripts and styles. Authentication is permitted by default; gate at the network
     * edge if docs should not be public.
     */
    @Bean
    @Order(1)
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    SecurityFilterChain docsSecurityFilterChain(
        final HttpSecurity http,
        final RequestIdFilter requestIdFilter
    ) throws Exception {
        http
            .securityMatcher(DOCS_PATHS)
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> commonHeaders(headers)
                .contentSecurityPolicy(csp -> csp.policyDirectives(DOCS_CSP))
                .addHeaderWriter(new StaticHeadersWriter(ROBOTS_HEADER, ROBOTS_NOINDEX)))
            .addFilterBefore(requestIdFilter, SecurityContextHolderFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Catch-all chain for application traffic. Stateless, JWT-authenticated, rate-limited at
     * the public endpoints, and locked down with a strict JSON-API CSP.
     */
    @Bean
    @Order(2)
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    SecurityFilterChain apiSecurityFilterChain(
        final HttpSecurity http,
        final JwtAuthenticationFilter jwtAuthenticationFilter,
        final RateLimitFilter rateLimitFilter,
        final RequestIdFilter requestIdFilter
    ) throws Exception {
        http
            .cors(Customizer.withDefaults())
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
            .addFilterBefore(requestIdFilter, SecurityContextHolderFilter.class)
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

    /**
     * Disables the auto-registration that would run {@link RequestIdFilter} twice. The filter
     * is wired into the security chain via {@code addFilterBefore} instead.
     */
    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(
        final RequestIdFilter requestIdFilter
    ) {
        final FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(requestIdFilter);
        registration.setEnabled(false);
        return registration;
    }
}
