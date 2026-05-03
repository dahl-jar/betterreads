package com.betterreads.operations.access;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Wires the Cloudflare Access JWT decoder when {@code cloudflare.access.aud} and
 * {@code cloudflare.access.team-domain} are both configured.
 *
 * <p>The decoder fetches Cloudflare's JWKS from the team domain, validates RS256 signatures,
 * checks the timestamp claims via Spring's defaults, and runs the AUD validator. When the
 * properties are not set, no {@link JwtDecoder} bean is created; {@code SecurityConfig} detects
 * the absence and leaves the management chain at {@code permitAll} for local dev.
 */
@Configuration
@EnableConfigurationProperties(CloudflareAccessProperties.class)
public class CloudflareAccessConfig {

    /**
     * Creates the {@link JwtDecoder} that {@code SecurityConfig} uses on the management chain.
     * Only registered when both env vars are non-blank.
     */
    /**
     * Creates the {@link JwtDecoder} that {@code SecurityConfig} uses on the management chain.
     * Returns {@code null} when {@code cloudflare.access.aud} or {@code team-domain} is blank
     * so {@code SecurityConfig} can detect the absence and leave the chain at {@code permitAll}.
     */
    @Bean
    @Nullable
    JwtDecoder cloudflareAccessJwtDecoder(final CloudflareAccessProperties properties) {
        if (!properties.isEnabled()) {
            return null;
        }
        final NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(properties.jwkSetUri())
            .build();
        final OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefault();
        final OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(
            defaults,
            new CloudflareAccessAudienceValidator(requireAudience(properties))
        );
        decoder.setJwtValidator(withAudience);
        return decoder;
    }

    private static String requireAudience(final CloudflareAccessProperties properties) {
        final String audience = properties.aud();
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException("cloudflare.access.aud is required when the decoder is enabled");
        }
        return audience;
    }
}
