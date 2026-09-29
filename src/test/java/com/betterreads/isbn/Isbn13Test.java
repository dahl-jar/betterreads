package com.betterreads.isbn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Isbn13Test {

    @ParameterizedTest
    @CsvSource({"9780345539786, true", "9780553103540, true", "9783453315617, true", "9780000000040, true",
        "9780345539787, false", "978034553978, false"})
    void shouldCheckTheCheckDigit(final String isbn, final boolean valid) {
        final boolean result = Isbn13.isValid(isbn);

        assertThat(result).isEqualTo(valid);
    }
}
