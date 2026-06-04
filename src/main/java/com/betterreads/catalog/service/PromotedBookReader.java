package com.betterreads.catalog.service;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.catalog.mapper.BookDetailMapper;
import com.betterreads.catalog.repository.BookRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a promoted book by key, caching the result.
 *
 * <p>Separate bean so the {@code @Cacheable} proxy applies: a call from within
 * {@link BookReadService} to a cached method on the same bean would bypass the proxy and never
 * cache. An absent book returns null and is not cached, since it may be promoted later.
 */
@Service
public class PromotedBookReader {

    private final BookRepository books;

    private final BookDetailMapper mapper;

    public PromotedBookReader(final BookRepository books, final BookDetailMapper mapper) {
        this.books = books;
        this.mapper = mapper;
    }

    /** Returns the promoted book mapped to its detail response, or null when no book matches the key. */
    @Cacheable(cacheNames = "bookDetails", unless = "#result == null")
    @Transactional(readOnly = true)
    @Nullable
    public BookDetailResponse findByKey(final String key) {
        return books.findByDedupKey(key).map(mapper::fromBook).orElse(null);
    }
}
