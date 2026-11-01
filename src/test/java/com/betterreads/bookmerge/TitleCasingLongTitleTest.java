package com.betterreads.bookmerge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TitleCasingLongTitleTest {

    private static final int WORDS = 100_000;

    private static final long LIMIT_SECONDS = 10;

    @Test
    @Timeout(value = LIMIT_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void shouldCaseLongAllCapsTitleWithoutStalling() {
        final String title = "AB ".repeat(WORDS) + "Z";

        final String cased = TitleCasing.fixAllCaps(title, "en");

        assertThat(cased).startsWith("Ab Ab").hasSameSizeAs(title);
    }
}
