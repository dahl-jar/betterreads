package com.betterreads.features.coverimages;

import java.util.Optional;

import com.betterreads.book.Book;
import com.betterreads.images.Image;
import com.betterreads.images.ImageStore;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Loads a book's cover bytes, mirroring on a miss.
 *
 * <p>A book still in staging takes its cover URL from the seed, so a seed served before promotion
 * still gets a cover.
 */
@Service
class CoverImageService {

    private final BookCoverRepository books;

    private final PendingBookRepository pendingBooks;

    private final ImageStore imageStore;

    private final CoverMirrorService coverMirror;

    CoverImageService(
        final BookCoverRepository books,
        final PendingBookRepository pendingBooks,
        final ImageStore imageStore,
        final CoverMirrorService coverMirror
    ) {
        this.books = books;
        this.pendingBooks = pendingBooks;
        this.imageStore = imageStore;
        this.coverMirror = coverMirror;
    }

    /** The object key comes from the current cover URL, so a changed cover misses and re-mirrors. */
    public Optional<Image> loadCover(final String key) {
        final String coverUrl = sourceCoverUrl(key);
        if (coverUrl == null || coverUrl.isBlank()) {
            return Optional.empty();
        }
        return imageStore.get(CoverMirrorService.objectKey(key, coverUrl))
            .or(() -> coverMirror.mirror(key, coverUrl).flatMap(imageStore::get));
    }

    private @Nullable String sourceCoverUrl(final String key) {
        return books.findByDedupKey(key)
            .map(Book::getCoverUrl)
            .orElseGet(() -> pendingBooks.findByDedupKey(key)
                .map(PendingBook::getCoverUrl)
                .orElse(null));
    }
}
