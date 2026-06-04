package com.betterreads.catalog.service.read;

import java.util.Optional;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.catalog.mapper.BookDetailMapper;
import com.betterreads.catalog.repository.PendingBookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves book detail for the read endpoint, preferring the promoted catalog book and falling back
 * to the staging seed so a just-staged book is still readable while enrichment runs.
 *
 * <p>Only the promoted branch is cached; a seed is served straight from the database because it
 * changes as enrichment fills it.
 */
@Service
public class BookReadService {

    private final PromotedBookReader promotedBookReader;

    private final PendingBookRepository pendingBooks;

    private final BookDetailMapper mapper;

    public BookReadService(
        final PromotedBookReader promotedBookReader,
        final PendingBookRepository pendingBooks,
        final BookDetailMapper mapper
    ) {
        this.promotedBookReader = promotedBookReader;
        this.pendingBooks = pendingBooks;
        this.mapper = mapper;
    }

    /** Returns the promoted book, or the staging seed, or empty when the key is unknown. */
    @Transactional(readOnly = true)
    public Optional<BookDetailResponse> findByKey(final String key) {
        final BookDetailResponse promoted = promotedBookReader.findByKey(key);
        if (promoted != null) {
            return Optional.of(promoted);
        }
        return pendingBooks.findByDedupKey(key).map(mapper::fromPending);
    }
}
