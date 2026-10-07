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

    @ParameterizedTest
    @CsvSource({"9780345539786, 0345539788", "9780804429573, 080442957X", "9780000000002, 0000000000",
        "9798000000000,"})
    void shouldConvertToIsbn10(final String isbn13, final String isbn10) {
        final String result = Isbn13.toIsbn10(isbn13);

        assertThat(result).isEqualTo(isbn10);
    }

    @ParameterizedTest
    @CsvSource({"0345539788, 9780345539786", "080442957X, 9780804429573", "0345539787,", "03455397X8,",
        "034553978,"})
    void shouldConvertFromIsbn10(final String isbn10, final String isbn13) {
        final String result = Isbn13.fromIsbn10(isbn10);

        assertThat(result).isEqualTo(isbn13);
    }
}
