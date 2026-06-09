package com.betterreads.catalog.service.read;

import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.source.CoverMirrorService;
import com.betterreads.integration.minio.ImageStore;
import com.betterreads.integration.minio.StoredImage;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Resolves a book's cover bytes for the image endpoint, mirroring on a miss.
 *
 * <p>The object is read from storage first; when it is not yet stored, the external cover is mirrored
 * once and re-read, so a cover the backfill has not reached self-heals on first request. The source
 * URL is taken from the promoted book, or from its staging seed when it is not yet promoted, so a
 * seed served before promotion still resolves its cover. A book with no cover, or one whose external
 * cover cannot be downloaded, resolves to empty.
 */
@Service
public class CoverImageService {

    private final BookRepository books;

    private final PendingBookRepository pendingBooks;

    private final ImageStore imageStore;

    private final CoverMirrorService coverMirror;

    public CoverImageService(
        final BookRepository books,
        final PendingBookRepository pendingBooks,
        final ImageStore imageStore,
        final CoverMirrorService coverMirror
    ) {
        this.books = books;
        this.pendingBooks = pendingBooks;
        this.imageStore = imageStore;
        this.coverMirror = coverMirror;
    }

    /**
     * Returns the stored cover for the book key, mirroring on a miss, or empty when none resolves.
     *
     * <p>The object key is resolved from the book's current cover URL, so a cover that has changed
     * since it was last mirrored reads a fresh key, misses, and re-mirrors, rather than serving the
     * object stored for the old URL.
     */
    public Optional<StoredImage> loadCover(final String key) {
        final String coverUrl = sourceCoverUrl(key);
        if (coverUrl == null || coverUrl.isBlank()) {
            return Optional.empty();
        }
        final String objectKey = CoverMirrorService.objectKey(key, coverUrl);
        final Optional<StoredImage> stored = imageStore.get(objectKey);
        if (stored.isPresent()) {
            return stored;
        }
        return coverMirror.mirror(key, coverUrl).flatMap(imageStore::get);
    }

    private @Nullable String sourceCoverUrl(final String key) {
        return books.findByDedupKey(key)
            .map(Book::getCoverUrl)
            .orElseGet(() -> pendingBooks.findByDedupKey(key)
                .map(PendingBook::getCoverUrl)
                .orElse(null));
    }
}
