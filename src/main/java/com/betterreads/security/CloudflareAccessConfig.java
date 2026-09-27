package com.betterreads.security;

import java.util.Objects;

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

/** Cloudflare Access token decoder for the actuator endpoints. */
@Configuration
@EnableConfigurationProperties(CloudflareAccessProperties.class)
class CloudflareAccessConfig {

    /** null when {@code aud} or {@code team-domain} is blank */
    @Bean
    @Nullable
    JwtDecoder cloudflareAccessJwtDecoder(final CloudflareAccessProperties properties) {
        if (!properties.isEnabled()) {
            return null;
        }
        final NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(properties.jwkSetUri())
            .build();
        return withAudienceCheck(decoder, Objects.requireNonNull(properties.aud()));
    }

    static NimbusJwtDecoder withAudienceCheck(final NimbusJwtDecoder decoder, final String aud) {
        final OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefault();
        final OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(
            defaults,
            new CloudflareAccessAudienceValidator(aud)
        );
        decoder.setJwtValidator(withAudience);
        return decoder;
    }
}
