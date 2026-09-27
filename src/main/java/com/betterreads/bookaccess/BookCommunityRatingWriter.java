package com.betterreads.bookaccess;

import java.math.BigDecimal;

import com.betterreads.book.Book;
import com.betterreads.book.BookDetailCache;
import com.betterreads.book.BookRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores a book's community rating aggregate. */
@Service
public class BookCommunityRatingWriter {

    private final BookRepository books;

    private final CacheManager cacheManager;

    public BookCommunityRatingWriter(final BookRepository books, final CacheManager cacheManager) {
        this.books = books;
        this.cacheManager = cacheManager;
    }

    /**
     * Two concurrent ratings would each save an aggregate missing the other's review, so the book
     * row is locked. Joins the caller's transaction so the lock holds through the review write.
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
