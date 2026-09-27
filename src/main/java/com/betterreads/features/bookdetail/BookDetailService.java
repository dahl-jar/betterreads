package com.betterreads.features.bookdetail;

import java.util.Optional;

import com.betterreads.pendingbook.PendingBookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves book detail from the promoted catalog book, falling back to the staging seed.
 *
 * <p>Only the promoted book is cached. A seed changes as its missing fields are filled, so it is
 * read from the database every time.
 */
@Service
class BookDetailService {

    private final PromotedBookReader promotedBookReader;

    private final PendingBookRepository pendingBooks;

    private final BookDetailMapper mapper;

    public BookDetailService(
        final PromotedBookReader promotedBookReader,
        final PendingBookRepository pendingBooks,
        final BookDetailMapper mapper
    ) {
        this.promotedBookReader = promotedBookReader;
        this.pendingBooks = pendingBooks;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<BookDetailResponse> findByKey(final String key) {
        return Optional.ofNullable(promotedBookReader.findByKey(key))
            .or(() -> pendingBooks.findByDedupKey(key).map(mapper::fromPending));
    }
}
