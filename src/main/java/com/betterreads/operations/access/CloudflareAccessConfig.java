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
 * Wires a {@link JwtDecoder} for Cloudflare Access when {@code cloudflare.access.aud} and
 * {@code cloudflare.access.team-domain} are both set. Decoder fetches the team JWKS, validates
 * RS256 signatures, applies Spring's default timestamp checks, then enforces AUD.
 */
@Configuration
@EnableConfigurationProperties(CloudflareAccessProperties.class)
public class CloudflareAccessConfig {

    /**
     * Returns the configured {@link JwtDecoder}, or {@code null} when {@code aud} or
     * {@code teamDomain} is blank so {@code SecurityConfig} can leave the management chain at
     * {@code permitAll}.
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
