package com.betterreads.features.bookdetail;

import com.betterreads.book.BookDetailCache;
import com.betterreads.book.BookRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a promoted book by key, caching the result.
 *
 * <p>Its own bean so calls go through the cache proxy. An absent book returns null and is not
 * cached, since it may be promoted later.
 */
@Service
class PromotedBookReader {

    private final BookRepository books;

    private final BookDetailMapper mapper;

    public PromotedBookReader(final BookRepository books, final BookDetailMapper mapper) {
        this.books = books;
        this.mapper = mapper;
    }

    @Cacheable(cacheNames = BookDetailCache.NAME, unless = "#result == null")
    @Transactional(readOnly = true)
    @Nullable
    public BookDetailResponse findByKey(final String key) {
        return books.findByDedupKey(key).map(mapper::fromBook).orElse(null);
    }
}
