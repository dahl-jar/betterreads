package com.betterreads.booksource;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MergedBookTest {

    private static final String HARDCOVER_ID = "hc-1";

    private static final MergedBook MERGED = new MergedBook(
        SourceBook.builder(BookFieldSource.HARDCOVER).hardcoverId(HARDCOVER_ID).title("Edgedancer").build(),
        Map.of(), Set.of(), Set.of(BookFieldSource.HARDCOVER));

    @Test
    void shouldDenySeriesAuthorityForAnotherHardcoverBook() {
        final boolean authority = MERGED.hasSeriesAuthority("hc-2");

        assertThat(authority).isFalse();
    }

    @Test
    void shouldDenySeriesAuthorityWhenHardcoverMissed() {
        final MergedBook missed = MERGED.withResolvedSources(Set.of(BookFieldSource.OPEN_LIBRARY));

        final boolean authority = missed.hasSeriesAuthority(HARDCOVER_ID);

        assertThat(authority).isFalse();
    }

    @Test
    void shouldGrantSeriesAuthorityForTheSameHardcoverBook() {
        final boolean authority = MERGED.hasSeriesAuthority(HARDCOVER_ID);

        assertThat(authority).isTrue();
    }
}
