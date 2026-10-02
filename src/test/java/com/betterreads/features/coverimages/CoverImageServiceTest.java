package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverBackfillServiceTest.COVER_URL;
import static com.betterreads.features.coverimages.CoverBackfillServiceTest.KEY;
import static com.betterreads.features.coverimages.CoverBackfillServiceTest.book;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverImageServiceTest {

    private static final long BOOK_ID = 1L;

    private static final String OBJECT_KEY = CoverMirrorService.objectKey(KEY, COVER_URL);

    private static final String JPEG_TYPE = "image/jpeg";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8};

    private final BookCoverRepository books = mock(BookCoverRepository.class);

    private final PendingBookRepository pendingBooks = mock(PendingBookRepository.class);

    private final ImageStore imageStore = mock(ImageStore.class);

    private final CoverMirrorService coverMirror = mock(CoverMirrorService.class);

    private final CoverImageService service =
        new CoverImageService(books, pendingBooks, imageStore, coverMirror);

    @Test
    @DisplayName("a stored cover is served without mirroring")
    void servesStoredCover() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book(BOOK_ID)));
        when(imageStore.get(OBJECT_KEY)).thenReturn(Optional.of(new Image(JPEG, JPEG_TYPE)));

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).get().extracting(Image::contentType).isEqualTo(JPEG_TYPE);
        verify(coverMirror, never()).mirror(eq(KEY), eq(COVER_URL));
    }

    @Test
    @DisplayName("an un-stored cover is mirrored from the promoted book then served")
    void mirrorsPromotedBookOnMiss() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book(BOOK_ID)));
        stubMirrorOnMiss();

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isPresent();
        verify(coverMirror).mirror(KEY, COVER_URL);
    }

    @Test
    @DisplayName("a not-yet-promoted book resolves its source cover from the staging seed")
    void mirrorsStagingSeedOnMiss() {
        final PendingBook seed = new PendingBook();
        seed.setDedupKey(KEY);
        seed.setCoverUrl(COVER_URL);
        when(books.findByDedupKey(KEY)).thenReturn(Optional.empty());
        when(pendingBooks.findByDedupKey(KEY)).thenReturn(Optional.of(seed));
        stubMirrorOnMiss();

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isPresent();
        verify(coverMirror).mirror(KEY, COVER_URL);
    }

    @Test
    @DisplayName("an unknown key with no stored cover resolves to empty")
    void unknownKeyIsEmpty() {
        when(books.findByDedupKey(KEY)).thenReturn(Optional.empty());
        when(pendingBooks.findByDedupKey(KEY)).thenReturn(Optional.empty());

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isEmpty();
    }

    @Test
    @DisplayName("a blank cover url is never sent to the mirror")
    void shouldNotMirrorWhenCoverUrlIsBlank() {
        final Book blankCover = book(BOOK_ID);
        blankCover.setCoverUrl(" ");
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(blankCover));

        final Optional<Image> cover = service.loadCover(KEY);

        assertThat(cover).isEmpty();
        verify(coverMirror, never()).mirror(any(), any());
    }

    private void stubMirrorOnMiss() {
        when(imageStore.get(OBJECT_KEY))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(new Image(JPEG, JPEG_TYPE)));
        when(coverMirror.mirror(KEY, COVER_URL)).thenReturn(Optional.of(OBJECT_KEY));
    }
}
