package com.betterreads.booksource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeriesEntryTest {

    @ParameterizedTest(name = "position {0} is labelled {1}")
    @CsvSource({"2.5, Red Rising Saga #2.5", "10, Red Rising Saga #10"})
    void shouldLabelThePositionWithoutTrailingZeros(final double position, final String label) {
        final SeriesEntry entry = new SeriesEntry("Red Rising Saga", position);

        final String text = entry.label();

        assertThat(text).isEqualTo(label);
    }
}
