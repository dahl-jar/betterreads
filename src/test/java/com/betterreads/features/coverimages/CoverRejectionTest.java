package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.BOOK_ID;
import static com.betterreads.features.coverimages.CoverImageFixtures.GOOGLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import com.betterreads.book.Book;
import org.junit.jupiter.api.Test;

class CoverRejectionTest {

    private final CoverBlockRepository blocks = mock(CoverBlockRepository.class);

    private final CoverCheck check = mock(CoverCheck.class);

    private final Set<String> failing = new HashSet<>();

    private final CoverPicker picker = new CoverPicker(
        mock(CoverSources.class), blocks, CoverImageFixtures.failingFetcher(failing), check);

    private final Book book = CoverImageFixtures.book(BOOK_ID, GOOGLE_COVER);

    @Test
    void shouldRejectABlockedCurrentCover() {
        when(blocks.isBlocked(GOOGLE_COVER)).thenReturn(true);

        final boolean rejected = picker.rejectsCurrent(book);

        assertThat(rejected).isTrue();
    }

    @Test
    void shouldRejectAWrongCurrentImage() {
        when(check.isWrongImage(bytes(GOOGLE_COVER))).thenReturn(true);

        final boolean rejected = picker.rejectsCurrent(book);

        assertThat(rejected).isTrue();
    }

    @Test
    void shouldKeepACurrentCoverItCannotFetch() {
        failing.add(GOOGLE_COVER);
        when(check.isWrongImage(any())).thenReturn(true);

        final boolean rejected = picker.rejectsCurrent(book);

        assertThat(rejected).isFalse();
    }

    @Test
    void shouldNotRejectABookWithoutACover() {
        final Book uncovered = CoverImageFixtures.book(BOOK_ID, null);
        when(check.isWrongImage(any())).thenReturn(true);

        final boolean rejected = picker.rejectsCurrent(uncovered);

        assertThat(rejected).isFalse();
    }
}
