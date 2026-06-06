package com.betterreads.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The refresh cookie cannot be SameSite=None without Secure, since browsers drop such a cookie; the
 * properties reject that combination at construction so a misconfigured environment fails fast.
 */
class RefreshCookiePropertiesTest {

    private static final String NONE = "None";

    private static final String STRICT = "Strict";

    @Test
    @DisplayName("SameSite=None without Secure is rejected")
    void sameSiteNoneWithoutSecureIsRejected() {
        assertThatThrownBy(() -> new RefreshCookieProperties(false, NONE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same-site=None requires");
    }

    @Test
    @DisplayName("SameSite=None with Secure is accepted")
    void sameSiteNoneWithSecureIsAccepted() {
        final RefreshCookieProperties props = new RefreshCookieProperties(true, NONE);

        assertThat(props.sameSite()).isEqualTo(NONE);
    }

    @Test
    @DisplayName("SameSite=Strict without Secure is allowed, since Strict does not require it")
    void sameSiteStrictWithoutSecureIsAllowed() {
        final RefreshCookieProperties props = new RefreshCookieProperties(false, STRICT);

        assertThat(props.sameSite()).isEqualTo(STRICT);
    }
}
