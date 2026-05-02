package com.betterreads.config;

import com.betterreads.common.web.RequestIdFilter;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Wires the CORS allow list from {@link CorsProperties} into Spring Security.
 */
@Configuration
public class CorsConfig {

    private static final Duration MAX_AGE = Duration.ofHours(1);

    private static final List<String> ALLOWED_METHODS = List.of(
        HttpMethod.GET.name(),
        HttpMethod.POST.name(),
        HttpMethod.PUT.name(),
        HttpMethod.PATCH.name(),
        HttpMethod.DELETE.name(),
        HttpMethod.OPTIONS.name()
    );

    private static final List<String> ALLOWED_HEADERS = List.of(
        HttpHeaders.AUTHORIZATION,
        HttpHeaders.CONTENT_TYPE,
        RequestIdFilter.HEADER
    );

    private static final List<String> EXPOSED_HEADERS = List.of(RequestIdFilter.HEADER);

    /**
     * Builds a configuration source applied to every path. {@link CorsProperties} validates
     * the origin list at startup, so by the time this bean runs the list is either empty
     * (no browser origin allowed) or a list of exact-match origins. Credentials are allowed
     * because the frontend sends {@code Authorization: Bearer <jwt>} on every authenticated
     * call; without that flag, the browser drops the header on cross-origin requests.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(final CorsProperties corsProperties) {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(MAX_AGE);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
