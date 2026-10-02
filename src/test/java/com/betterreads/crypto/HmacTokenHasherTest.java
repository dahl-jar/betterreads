package com.betterreads.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.security.JwtProperties;

import org.junit.jupiter.api.Test;

final class HmacTokenHasherTest {

    private static final String ISSUER = "betterreads";

    private static final String RFC_4231_KEY = "Jefe";

    private static final String RFC_4231_MESSAGE = "what do ya want for nothing?";

    private static final String RFC_4231_HMAC_SHA256 =
        "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";

    @Test
    void matchesHmacSha256KnownAnswerVector() {
        final JwtProperties properties = new JwtProperties(RFC_4231_KEY, ISSUER, 1, 1, 1);
        final HmacTokenHasher hasher = new HmacTokenHasher(properties);

        final String digest = hasher.hash(RFC_4231_MESSAGE);

        assertThat(digest).isEqualTo(RFC_4231_HMAC_SHA256);
    }
}
