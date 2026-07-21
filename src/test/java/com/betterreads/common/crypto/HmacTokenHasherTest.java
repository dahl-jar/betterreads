package com.betterreads.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class HmacTokenHasherTest {

    private static final String RFC_4231_KEY = "Jefe";

    private static final String RFC_4231_MESSAGE = "what do ya want for nothing?";

    private static final String RFC_4231_HMAC_SHA256 =
        "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";

    @Test
    void matchesHmacSha256KnownAnswerVector() {
        final HmacTokenHasher hasher = new HmacTokenHasher(RFC_4231_KEY);

        final String digest = hasher.hash(RFC_4231_MESSAGE);

        assertThat(digest).isEqualTo(RFC_4231_HMAC_SHA256);
    }
}
