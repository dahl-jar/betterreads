package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.COVER_URL;
import static com.betterreads.features.coverimages.CoverImageFixtures.KEY;
import static com.betterreads.features.coverimages.CoverImageFixtures.book;
import static com.betterreads.features.coverimages.CoverImageFixtures.BOOK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.betterreads.book.Book;
import com.betterreads.images.Image;
import com.betterreads.images.ImageStore;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.pendingbook.PendingBookStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoverImageServiceTest {

    private static final String OBJECT_KEY = CoverMirrorService.objectKey(KEY, COVER_URL);

    private static final String JPEG_TYPE = "image/jpeg";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8};

    private final BookCoverRepository books = mock(BookCoverRepository.class);

    private final PendingBookRepository pendingBooks = mock(PendingBookRepository.class);

    private final ImageStore imageStore = mock(ImageStore.class);

    private final CoverMirrorGate mirrorGate = mock(CoverMirrorGate.class);

    private final CoverBlockRepository blocks = mock(CoverBlockRepository.class);

    private final CoverImageService service =
        new CoverImageService(books, pendingBooks, blocks, imageStore, mirrorGate);

    @Test
    @DisplayName("a stored cover is served without mirroring")
    void servesStoredCover() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book(BOOK_ID)));
        when(imageStore.get(OBJECT_KEY)).thenReturn(Optional.of(new Image(JPEG, JPEG_TYPE)));

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).get().extracting(Image::contentType).isEqualTo(JPEG_TYPE);
        verify(mirrorGate, never()).mirror(KEY, COVER_URL);
    }

    @Test
    @DisplayName("an un-stored cover is mirrored from the promoted book then served")
    void mirrorsPromotedBookOnMiss() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book(BOOK_ID)));
        stubMirrorOnMiss();

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isPresent();
        verify(mirrorGate).mirror(KEY, COVER_URL);
    }

    @Test
    @DisplayName("a not-yet-promoted book resolves its source cover from the staging seed")
    void mirrorsStagingSeedOnMiss() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.empty());
        when(pendingBooks.findByDedupKey(KEY)).thenReturn(Optional.of(seed()));
        stubMirrorOnMiss();

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isPresent();
        verify(mirrorGate).mirror(KEY, COVER_URL);
    }

    @Test
    @DisplayName("a blank cover url is never sent to the mirror")
    void shouldNotMirrorWhenCoverUrlIsBlank() {
        final Book blankCover = book(BOOK_ID);
        blankCover.setCoverUrl(" ");
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(blankCover));

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isEmpty();
        verify(mirrorGate, never()).mirror(any(), any());
    }

    @Test
    void shouldNotServeTheStagingCoverOfABookWhoseCoverWasCleared() {
        final Book cleared = book(BOOK_ID);
        cleared.setCoverUrl(null);
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(cleared));
        when(pendingBooks.findByDedupKey(KEY)).thenReturn(Optional.of(seed()));
        stubMirrorOnMiss();

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isEmpty();
    }

    @Test
    void shouldNotServeABlockedCover() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book(BOOK_ID)));
        when(blocks.isBlocked(COVER_URL)).thenReturn(true);
        stubMirrorOnMiss();

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {PendingBookStatus.PROMOTED, PendingBookStatus.DUPLICATE})
    void shouldNotServeTheCoverOfAMergedSeed(final String status) {
        final PendingBook merged = seed();
        merged.setStatus(status);
        when(books.findByDedupKey(KEY)).thenReturn(Optional.empty());
        when(pendingBooks.findByDedupKey(KEY)).thenReturn(Optional.of(merged));
        stubMirrorOnMiss();

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isEmpty();
    }

    private static PendingBook seed() {
        final PendingBook seed = new PendingBook();
        seed.setDedupKey(KEY);
        seed.setCoverUrl(COVER_URL);
        seed.setStatus(PendingBookStatus.PENDING);
        return seed;
    }

    private void stubMirrorOnMiss() {
        when(imageStore.get(OBJECT_KEY))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(new Image(JPEG, JPEG_TYPE)));
        when(mirrorGate.mirror(KEY, COVER_URL)).thenReturn(Optional.of(OBJECT_KEY));
    }
}
