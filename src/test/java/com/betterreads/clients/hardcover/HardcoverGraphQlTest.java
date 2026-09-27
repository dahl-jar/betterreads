package com.betterreads.clients.hardcover;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class HardcoverGraphQlTest {

    private static final int ABSOLUTE_BATMAN_ID = 2_235_304;

    @Test
    void shouldParseNumericId() {
        assertThat(HardcoverGraphQl.parseId("2235304")).contains(ABSOLUTE_BATMAN_ID);
    }

    @Test
    void shouldReturnEmptyForMissingId() {
        assertThat(HardcoverGraphQl.parseId(null)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNonNumericId() {
        final Optional<Integer> id = HardcoverGraphQl.parseId("abs-batman");

        assertThat(id).isEmpty();
    }
}
