package com.betterreads.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshCookiePropertiesTest {

    private static final String NONE = "None";

    private static final String STRICT = "Strict";

    @ParameterizedTest
    @ValueSource(strings = {"None", "none", "NONE"})
    @DisplayName("SameSite=None without Secure is rejected")
    void sameSiteNoneWithoutSecureIsRejected(final String sameSite) {
        assertThatThrownBy(() -> new RefreshCookieProperties(false, sameSite))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same-site=None requires");
    }

    @Test
    @DisplayName("SameSite=None with Secure is accepted")
    void sameSiteNoneWithSecureIsAccepted() {
        assertThatCode(() -> new RefreshCookieProperties(true, NONE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SameSite=Strict without Secure is allowed")
    void sameSiteStrictWithoutSecureIsAllowed() {
        assertThatCode(() -> new RefreshCookieProperties(false, STRICT)).doesNotThrowAnyException();
    }
}
