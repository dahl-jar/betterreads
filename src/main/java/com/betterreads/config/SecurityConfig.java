package com.betterreads.config;

import com.betterreads.auth.jwt.JwtAuthenticationFilter;
import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.common.web.RequestIdFilter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Three filter chains, ordered most-specific first.
 *
 * <p>Management covers the actuator on the internal port. Docs covers Swagger UI and uses a
 * CSP that allows inline scripts and styles. Api is the catch-all with JWT auth, rate
 * limiting, and a strict {@code default-src 'none'} CSP.
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
        "/api/v1/reviews/**",
        "/api/v1/comments/**"
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

    /**
     * The Cloudflare Access JWT decoder is optional; when absent, the actuator endpoints stay
     * at {@code permitAll}.
     *
     * <p>Resolved lazily inside the chain bean to avoid bean-creation-order issues.
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
     * Filter chain for the actuator endpoints, matched on port 8081.
     *
     * <p>Requires a Cloudflare Access JWT when a decoder is configured; otherwise {@code permitAll}.
     * {@code /actuator/prometheus} and {@code /actuator/health} skip the JWT check because port 8081
     * is not exposed through the tunnel, so only on-VM processes (Alloy, the systemd healthcheck)
     * can reach them.
     */
    @Bean
    @Order(0)
    SecurityFilterChain managementSecurityFilterChain(
        final HttpSecurity http,
        final RequestIdFilter requestIdFilter
    ) {
        final JwtDecoder decoder = cloudflareAccessJwtDecoderProvider.getIfAvailable();
        http
            .securityMatcher(request ->
                request.getLocalPort() == managementPort
                    && EndpointRequest.toAnyEndpoint().matches(request))
            // NOTE(csrf): stateless, 127.0.0.1-bound, header-auth only. lgtm[java/spring-disabled-csrf-protection]
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

    /**
     * Filter chain for Swagger UI and OpenAPI doc paths.
     *
     * <p>CSP allows inline scripts and styles because the bundled Swagger UI loads them. No
     * authentication; gate at the network edge if docs should not be public.
     */
    @Bean
    @Order(1)
    SecurityFilterChain docsSecurityFilterChain(
        final HttpSecurity http,
        final RequestIdFilter requestIdFilter
    ) {
        http
            .securityMatcher(DOCS_PATHS)
            .cors(Customizer.withDefaults())
            // NOTE(csrf): read-only Swagger assets, no auth, no cookies. lgtm[java/spring-disabled-csrf-protection]
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

    /** Catch-all chain: stateless, JWT-authenticated, rate-limited, strict CSP. */
    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(
        final HttpSecurity http,
        final JwtAuthenticationFilter jwtAuthenticationFilter,
        final RateLimitFilter rateLimitFilter,
        final RequestIdFilter requestIdFilter
    ) {
        http
            .cors(Customizer.withDefaults())
            // NOTE(csrf): bearer JWT + SameSite=Strict refresh cookie. lgtm[java/spring-disabled-csrf-protection]
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
                .requestMatchers(HttpMethod.GET, PUBLIC_CATALOG_GET_PATHS).permitAll()
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
     * Stops Spring Boot from registering {@link RequestIdFilter} a second time on the servlet
     * chain; the security chain wires it via {@code addFilterBefore}.
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
