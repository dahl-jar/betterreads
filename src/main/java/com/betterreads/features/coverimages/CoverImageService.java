package com.betterreads.features.coverimages;

import java.util.Optional;
import java.util.Set;

import com.betterreads.images.Image;
import com.betterreads.images.ImageStore;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.pendingbook.PendingBookStatus;
import org.springframework.stereotype.Service;

/** Loads a book's cover bytes, mirroring a missing one when the gate allows. */
@Service
class CoverImageService {

    private static final Set<String> MERGED = Set.of(PendingBookStatus.PROMOTED, PendingBookStatus.DUPLICATE);

    private final BookCoverRepository books;

    private final PendingBookRepository pendingBooks;

    private final CoverBlockRepository blocks;

    private final ImageStore imageStore;

    private final CoverMirrorGate mirrorGate;

    CoverImageService(
        final BookCoverRepository books,
        final PendingBookRepository pendingBooks,
        final CoverBlockRepository blocks,
        final ImageStore imageStore,
        final CoverMirrorGate mirrorGate
    ) {
        this.books = books;
        this.pendingBooks = pendingBooks;
        this.blocks = blocks;
        this.imageStore = imageStore;
        this.mirrorGate = mirrorGate;
    }

    /** The object key comes from the current cover URL, so a changed cover misses and re-mirrors. */
    public Optional<Image> loadCover(final String key) {
        return sourceCoverUrl(key)
            .filter(url -> !url.isBlank() && !blocks.isBlocked(url))
            .flatMap(url -> imageStore.get(CoverMirrorService.objectKey(key, url))
                .or(() -> mirrorGate.mirror(key, url).flatMap(imageStore::get)));
    }

    private Optional<String> sourceCoverUrl(final String key) {
        return books.findByDedupKey(key)
            .map(book -> Optional.ofNullable(book.getCoverUrl()))
            .orElseGet(() -> pendingBooks.findByDedupKey(key)
                .filter(seed -> !MERGED.contains(seed.getStatus()))
                .map(PendingBook::getCoverUrl));
    }
}
