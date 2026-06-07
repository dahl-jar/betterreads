package com.betterreads.integration.googlebooks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The API key is trimmed at binding so a secret stored with a trailing newline does not produce a
 * {@code key=...\n} query param, which Spring's URI builder rejects with an
 * {@code IllegalArgumentException} on every Google Books request.
 */
class GoogleBooksPropertiesTest {

    private static final String BASE_URL = "https://www.googleapis.com/books/v1";

    private static final int CONNECT_TIMEOUT = 5_000;

    private static final int READ_TIMEOUT = 10_000;

    @Test
    @DisplayName("trims surrounding whitespace and newlines from the API key")
    void trimsApiKey() {
        final GoogleBooksProperties properties = new GoogleBooksProperties(
            BASE_URL, "AIzaSyExampleKey\n", CONNECT_TIMEOUT, READ_TIMEOUT);

        assertThat(properties.apiKey()).isEqualTo("AIzaSyExampleKey");
    }

    @Test
    @DisplayName("keeps a null key null so a key-less profile still boots")
    void keepsNullKey() {
        final GoogleBooksProperties properties = new GoogleBooksProperties(
            BASE_URL, null, CONNECT_TIMEOUT, READ_TIMEOUT);

        assertThat(properties.apiKey()).isNull();
    }
}
