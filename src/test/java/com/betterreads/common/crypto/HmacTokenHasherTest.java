package com.betterreads.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the HMAC-SHA256 hasher used to store every keyed token in the auth domain
 * (refresh, password-reset, email-verification). The contract is deterministic: the same
 * plaintext under the same key always produces the same digest, and different plaintexts
 * produce different digests.
 *
 * <p>HMAC behavior itself is library code; this test only guards against accidentally adding
 * salting or randomization that would break the database lookup, and against a future caller
 * swapping algorithms or encoding without the rest of the system noticing.
 */
@DisplayName("HmacTokenHasher")
final class HmacTokenHasherTest {

    private static final String SECRET = "integration-test-secret-must-be-at-least-256-bits-long-padding-padding";

    private static final String OTHER_SECRET = "alternate-secret-also-at-least-256-bits-long-with-extra-padding-pad";

    private static final String TOKEN_A = "token-a";

    private static final String TOKEN_B = "token-b";

    @Test
    void hashesDeterministicallyAndDistinctsBetweenInputs() {
        final HmacTokenHasher hasher = new HmacTokenHasher(SECRET);

        final String hashA1 = hasher.hash(TOKEN_A);
        final String hashA2 = hasher.hash(TOKEN_A);
        final String hashB = hasher.hash(TOKEN_B);

        assertThat(hashA1)
            .isEqualTo(hashA2)
            .isNotEqualTo(hashB);
    }

    @Test
    void differentSecretsProduceDifferentDigestsForTheSamePlaintext() {
        final HmacTokenHasher hasherOne = new HmacTokenHasher(SECRET);
        final HmacTokenHasher hasherTwo = new HmacTokenHasher(OTHER_SECRET);

        assertThat(hasherOne.hash(TOKEN_A))
            .as("the secret is part of the digest; rotating secrets must invalidate stored hashes")
            .isNotEqualTo(hasherTwo.hash(TOKEN_A));
    }
}
