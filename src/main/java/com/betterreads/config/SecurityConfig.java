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

/**
 * Three security filter chains, ordered most-specific first.
 *
 * <p>{@code managementSecurityFilterChain} matches actuator paths but only when the request
 * arrived on the management port (bound to {@code 127.0.0.1:8081}); requests reach it through
 * Cloudflare Tunnel and Access in production, so the chain itself permits all. The docs chain
 * relaxes CSP for Swagger UI. The api chain is the catch-all for everything else and runs JWT
 * + rate limiting + the strict JSON-API CSP.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public final class SecurityConfig {

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
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/healthz"
    };

    private final int managementPort;

    private final ObjectProvider<JwtDecoder> cloudflareAccessJwtDecoderProvider;

    /**
     * Builds the config with the management port and a provider for the optional Cloudflare
     * Access JWT decoder. The decoder bean only exists when {@code cloudflare.access.aud} and
     * {@code cloudflare.access.team-domain} are configured (see {@code CloudflareAccessConfig}).
     * When absent, the management chain stays at {@code permitAll}, which is correct for local dev.
     *
     * <p>The provider is resolved lazily inside {@link #managementSecurityFilterChain} so the
     * decoder bean has time to be created before lookup.
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
     * Owns actuator paths, but only when the request arrives on the management port. The
     * management connector is bound to {@code 127.0.0.1:8081} so external traffic cannot
     * reach it directly; production access goes through Cloudflare Tunnel and Access.
     *
     * <p>When the Cloudflare Access JWT decoder is configured (production), this chain
     * requires a valid {@code Cf-Access-Jwt-Assertion} header signed by Cloudflare and
     * carrying the configured AUD. When the decoder is absent (local dev), the chain stays
     * at {@code permitAll} so Prometheus and curl-against-localhost still work.
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
                .contentSecurityPolicy(csp -> csp.policyDirectives(DOCS_CSP)))
            .addFilterBefore(requestIdFilter, SecurityContextHolderFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

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
     * Stops Spring Boot from registering {@link RequestIdFilter} as a servlet-level filter.
     * Without this, the {@code @Component} gets picked up by both the servlet container and
     * the {@code addFilterBefore} call below, and the filter runs twice per request. We want
     * it on the security chain only.
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
