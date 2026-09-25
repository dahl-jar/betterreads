package com.betterreads.catalog.service.source.quality;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IsbnLanguageTest {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
        "9780385470025, en",
        "9781439153956, en",
        "9798888888888, en",
        "9782365772068, fr",
        "9783426281550, de",
        "9784087600087, ja",
        "9787020000005, zh",
        "9788200000006, no",
        "9788445076361, es",
        "9788700000001, da",
        "9788804668237, it",
        "9789100000004, sv"
    })
    void shouldMapRegistrationGroupToLanguage(final String isbn13, final String language) {
        assertThat(IsbnLanguage.languageOf(isbn13)).isEqualTo(language);
    }

    @Test
    void shouldLeaveLanguageUnknownForMultilingualGroup() {
        assertThat(IsbnLanguage.languageOf("9785170000000")).isNull();
    }

    @Test
    void shouldLeaveLanguageUnknownForMalformedIsbn() {
        assertThat(IsbnLanguage.languageOf("978038547002")).isNull();
    }
}
