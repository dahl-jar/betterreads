package com.betterreads.catalog.service.source.quality;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageCodesTest {

    @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
    @CsvSource({
        "eng,   en",
        "fre,   fr",
        "ger,   de",
        "spa,   es",
        "por,   pt",
        "en,    en",
        "EN,    en",
        "pt-BR, pt",
        "tlh,   tlh"
    })
    void shouldReduceToTwoLetterLanguageCode(final String raw, final String expected) {
        assertThat(LanguageCodes.iso6391(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" becomes null")
    @ValueSource(strings = {"und", "", "  "})
    void shouldDropUndeterminedAndBlankCodes(final String raw) {
        assertThat(LanguageCodes.iso6391(raw)).isNull();
    }
}
