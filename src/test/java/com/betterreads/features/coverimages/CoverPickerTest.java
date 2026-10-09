package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.betterreads.book.Book;
import com.betterreads.booksource.CoverSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoverPickerTest {

    private final CoverSources sources = mock(CoverSources.class);

    private final CoverBlockRepository blocks = mock(CoverBlockRepository.class);

    private final CoverCheck check = mock(CoverCheck.class);

    private final Set<String> failing = new HashSet<>();

    private final CoverPicker picker =
        new CoverPicker(sources, blocks, CoverImageFixtures.failingFetcher(failing), check);

    private final Book book = CoverImageFixtures.book(CoverImageFixtures.BOOK_ID, CoverImageFixtures.GOOGLE_COVER);

    @BeforeEach
    void setUp() {
        when(sources.find(CoverSource.APPLE_BOOKS, book)).thenReturn(Optional.of(CoverImageFixtures.APPLE_CANDIDATE));
        when(sources.find(CoverSource.HARDCOVER, book)).thenReturn(Optional.of(CoverImageFixtures.HARDCOVER_CANDIDATE));
        when(check.passes(bytes(CoverImageFixtures.APPLE_COVER))).thenReturn(true);
        when(check.passes(bytes(CoverImageFixtures.HARDCOVER_COVER))).thenReturn(true);
    }

    @Test
    void shouldPreferAppleOverHardcover() {
        final Optional<PickedCover> picked = picker.best(book);

        assertThat(picked.map(PickedCover::candidate)).contains(CoverImageFixtures.APPLE_CANDIDATE);
    }

    @Test
    void shouldFallBackWhenAppleFailsTheCheck() {
        when(check.passes(bytes(CoverImageFixtures.APPLE_COVER))).thenReturn(false);

        final Optional<PickedCover> picked = picker.best(book);

        assertThat(picked.map(PickedCover::candidate)).contains(CoverImageFixtures.HARDCOVER_CANDIDATE);
    }

    @Test
    void shouldFallBackWhenTheAppleImageCannotBeFetched() {
        failing.add(CoverImageFixtures.APPLE_COVER);

        final Optional<PickedCover> picked = picker.best(book);

        assertThat(picked.map(PickedCover::candidate)).contains(CoverImageFixtures.HARDCOVER_CANDIDATE);
    }

    @Test
    void shouldFallBackToOpenLibrary() {
        when(sources.find(CoverSource.OPEN_LIBRARY, book))
            .thenReturn(Optional.of(CoverImageFixtures.OPEN_LIBRARY_CANDIDATE));
        when(check.passes(bytes(CoverImageFixtures.APPLE_COVER))).thenReturn(false);
        when(check.passes(bytes(CoverImageFixtures.HARDCOVER_COVER))).thenReturn(false);
        when(check.passes(bytes(CoverImageFixtures.OPEN_LIBRARY_COVER))).thenReturn(true);

        final Optional<PickedCover> picked = picker.best(book);

        assertThat(picked.map(PickedCover::candidate)).contains(CoverImageFixtures.OPEN_LIBRARY_CANDIDATE);
    }

    @Test
    void shouldSkipABlockedCover() {
        when(blocks.isBlocked(CoverImageFixtures.APPLE_COVER)).thenReturn(true);

        final Optional<PickedCover> picked = picker.best(book);

        assertThat(picked.map(PickedCover::candidate)).contains(CoverImageFixtures.HARDCOVER_CANDIDATE);
    }

    @Test
    void shouldNotAskLaterSourcesAfterAPass() {
        picker.best(book);

        verify(sources, never()).find(CoverSource.HARDCOVER, book);
    }

    @Test
    void shouldCarryTheFetchedImage() {
        final Optional<PickedCover> picked = picker.best(book);

        assertThat(picked.map(cover -> cover.image().bytes())).contains(bytes(CoverImageFixtures.APPLE_COVER));
    }
}
