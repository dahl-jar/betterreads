package com.betterreads.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogSanitizerTest {

    @ParameterizedTest
    @ValueSource(strings = {"\r", "\n", "\u001B", " ", " "})
    void shouldRemoveControlCharacters(final String character) {
        final String value = "Du" + character + "ne";

        final String sanitized = LogSanitizer.forLog(value);

        assertThat(sanitized).isEqualTo("Dune");
    }
}
