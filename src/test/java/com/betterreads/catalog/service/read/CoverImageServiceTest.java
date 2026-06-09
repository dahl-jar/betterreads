package com.betterreads.catalog.service.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.source.CoverMirrorService;
import com.betterreads.integration.minio.ImageStore;
import com.betterreads.integration.minio.StoredImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The image service serves a stored cover directly, mirrors an un-stored one on first read, resolves
 * the source URL from a staging seed when the book is not yet promoted, and serves nothing for an
 * unknown key.
 */
class CoverImageServiceTest {

    private static final String KEY = "OL1W";

    private static final String COVER_URL = "https://covers.example.org/1.jpg";

    private static final String OBJECT_KEY = CoverMirrorService.objectKey(KEY, COVER_URL);

    private static final String JPEG_TYPE = "image/jpeg";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8};

    private final BookRepository books = mock(BookRepository.class);

    private final PendingBookRepository pendingBooks = mock(PendingBookRepository.class);

    private final ImageStore imageStore = mock(ImageStore.class);

    private final CoverMirrorService coverMirror = mock(CoverMirrorService.class);

    private final CoverImageService service =
        new CoverImageService(books, pendingBooks, imageStore, coverMirror);

    @Test
    @DisplayName("a stored cover is served without mirroring")
    void servesStoredCover() {
        final Book book = new Book();
        book.setDedupKey(KEY);
        book.setCoverUrl(COVER_URL);
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book));
        when(imageStore.get(OBJECT_KEY)).thenReturn(Optional.of(new StoredImage(JPEG, JPEG_TYPE)));

        final Optional<StoredImage> cover = service.loadCover(KEY);

        assertThat(cover).get().extracting(StoredImage::contentType).isEqualTo(JPEG_TYPE);
        verify(coverMirror, never()).mirror(eq(KEY), eq(COVER_URL));
    }

    @Test
    @DisplayName("a changed cover url reads a fresh key and re-mirrors, not the object stored for the old url")
    void changedUrlReMirrorsInsteadOfServingStale() {
        final String newUrl = "https://covers.example.org/2.jpg";
        final String newKey = CoverMirrorService.objectKey(KEY, newUrl);
        final Book book = new Book();
        book.setDedupKey(KEY);
        book.setCoverUrl(newUrl);
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book));
        when(imageStore.get(newKey))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(new StoredImage(JPEG, JPEG_TYPE)));
        when(coverMirror.mirror(KEY, newUrl)).thenReturn(Optional.of(newKey));

        final Optional<StoredImage> cover = service.loadCover(KEY);

        assertThat(cover).isPresent();
        verify(coverMirror).mirror(KEY, newUrl);
        verify(imageStore, never()).get(OBJECT_KEY);
    }

    @Test
    @DisplayName("an un-stored cover is mirrored from the promoted book then served")
    void mirrorsPromotedBookOnMiss() {
        final Book book = new Book();
        book.setDedupKey(KEY);
        book.setCoverUrl(COVER_URL);
        when(imageStore.get(OBJECT_KEY))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(new StoredImage(JPEG, JPEG_TYPE)));
        when(books.findByDedupKey(KEY)).thenReturn(Optional.of(book));
        when(coverMirror.mirror(KEY, COVER_URL)).thenReturn(Optional.of(OBJECT_KEY));

        final Optional<StoredImage> cover = service.loadCover(KEY);

        assertThat(cover).isPresent();
        verify(coverMirror).mirror(KEY, COVER_URL);
    }

    @Test
    @DisplayName("a not-yet-promoted book resolves its source cover from the staging seed")
    void mirrorsStagingSeedOnMiss() {
        final PendingBook seed = new PendingBook();
        seed.setDedupKey(KEY);
        seed.setCoverUrl(COVER_URL);
        when(imageStore.get(OBJECT_KEY))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(new StoredImage(JPEG, JPEG_TYPE)));
        when(books.findByDedupKey(KEY)).thenReturn(Optional.empty());
        when(pendingBooks.findByDedupKey(KEY)).thenReturn(Optional.of(seed));
        when(coverMirror.mirror(KEY, COVER_URL)).thenReturn(Optional.of(OBJECT_KEY));

        final Optional<StoredImage> cover = service.loadCover(KEY);

        assertThat(cover).isPresent();
        verify(coverMirror).mirror(KEY, COVER_URL);
    }

    @Test
    @DisplayName("an unknown key with no stored cover resolves to empty")
    void unknownKeyIsEmpty() {
        when(imageStore.get(OBJECT_KEY)).thenReturn(Optional.empty());
        when(books.findByDedupKey(KEY)).thenReturn(Optional.empty());
        when(pendingBooks.findByDedupKey(KEY)).thenReturn(Optional.empty());

        final Optional<StoredImage> cover = service.loadCover(KEY);

        assertThat(cover).isEmpty();
    }
}
