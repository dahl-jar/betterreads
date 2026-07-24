package com.betterreads.catalog.service.write;

import java.math.BigDecimal;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes a book's reader-community rating aggregate under a row lock and evicts its cached detail. */
@Service
public class BookCommunityRatingWriter {

    private final BookRepository books;

    private final CacheManager cacheManager;

    public BookCommunityRatingWriter(final BookRepository books, final CacheManager cacheManager) {
        this.books = books;
        this.cacheManager = cacheManager;
    }

    /**
     * Applies the community aggregate to the book under a row lock, so two concurrent rate writes do
     * not each persist an aggregate that misses the other's review. Joins the caller's transaction so
     * the lock holds through the review write that triggered the recompute.
     */
    @Transactional
    public void applyCommunityAggregate(
        final long bookId, final @Nullable BigDecimal average, final int count) {
        final Book locked = books.findForUpdate(bookId)
            .orElseThrow(() -> new IllegalStateException("rated book vanished bookId=" + bookId));
        locked.applyCommunityAggregate(average, count);
        books.save(locked);
        BookDetailCache.evict(cacheManager, locked.getDedupKey());
    }
}
