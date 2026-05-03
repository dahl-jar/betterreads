package com.betterreads.auth.refresh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the HMAC-SHA256 hasher used to store refresh tokens. The contract is deterministic:
 * the same plaintext under the same key always produces the same digest, and different
 * plaintexts produce different digests.
 *
 * <p>HMAC behavior itself is library code; this test only guards against accidentally adding
 * salting or randomization that would break the database lookup.
 */
@DisplayName("RefreshTokenHasher")
final class RefreshTokenHasherTest {

    private static final String SECRET = "integration-test-secret-must-be-at-least-256-bits-long-padding-padding";

    private static final String TOKEN_A = "token-a";

    private static final String TOKEN_B = "token-b";

    @Test
    void hashesDeterministicallyAndDistinctsBetweenInputs() {
        final RefreshTokenHasher hasher = new RefreshTokenHasher(SECRET);

        final String hashA1 = hasher.hash(TOKEN_A);
        final String hashA2 = hasher.hash(TOKEN_A);
        final String hashB = hasher.hash(TOKEN_B);

        assertThat(hashA1)
            .isEqualTo(hashA2)
            .isNotEqualTo(hashB);
    }
}
