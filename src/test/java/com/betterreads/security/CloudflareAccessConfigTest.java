package com.betterreads.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class CloudflareAccessConfigTest {

    private static final String AUD = "sun-eater-aud";

    private static final String OTHER_AUD = "red-rising-aud";

    private static final String TEAM_DOMAIN = "hadrian.example.test";

    private static final int KEY_SIZE = 2048;

    private static final long ONE_MINUTE_SECONDS = 60L;

    @Test
    void shouldBuildDecoderWhenAccessIsConfigured() {
        final CloudflareAccessProperties properties = new CloudflareAccessProperties(AUD, TEAM_DOMAIN);

        final JwtDecoder decoder = new CloudflareAccessConfig().cloudflareAccessJwtDecoder(properties);

        assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
    }

    @ParameterizedTest
    @CsvSource({
        "             , hadrian.example.test",
        "' '          , hadrian.example.test",
        "sun-eater-aud,                     ",
        "sun-eater-aud, ' '"
    })
    void shouldLeaveActuatorOpenWhenAccessIsBlank(final @Nullable String aud, final @Nullable String teamDomain) {
        final CloudflareAccessProperties properties = new CloudflareAccessProperties(aud, teamDomain);

        final JwtDecoder decoder = new CloudflareAccessConfig().cloudflareAccessJwtDecoder(properties);

        assertThat(decoder).isNull();
    }

    @Test
    void shouldFetchKeysFromTeamDomainCerts() {
        final CloudflareAccessProperties properties = new CloudflareAccessProperties(AUD, TEAM_DOMAIN);

        final String uri = properties.jwkSetUri();

        assertThat(uri).isEqualTo("https://hadrian.example.test/cdn-cgi/access/certs");
    }

    @Test
    void shouldRejectTokenForOtherAccessApp() throws JOSEException {
        final RSAKey key = new RSAKeyGenerator(KEY_SIZE).generate();
        final NimbusJwtDecoder signedBy = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        final NimbusJwtDecoder decoder = CloudflareAccessConfig.withAudienceCheck(signedBy, AUD);
        final String token = tokenFor(key, OTHER_AUD);

        assertThatThrownBy(() -> decoder.decode(token))
            .isInstanceOf(JwtValidationException.class)
            .hasMessageContaining("aud");
    }

    private static String tokenFor(final RSAKey key, final String audience) throws JOSEException {
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .audience(audience)
            .expirationTime(Date.from(Instant.now().plusSeconds(ONE_MINUTE_SECONDS)))
            .build();
        final SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
