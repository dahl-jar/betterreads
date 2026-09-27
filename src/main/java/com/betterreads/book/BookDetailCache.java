package com.betterreads.book;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** The cached book detail responses, evicted by the writes that change a book. */
public final class BookDetailCache {

    public static final String NAME = "bookDetails";

    private BookDetailCache() {
    }

    /** No-ops when the cache is not configured. */
    public static void evict(final CacheManager cacheManager, final String dedupKey) {
        final Cache cache = cacheManager.getCache(NAME);
        if (cache != null) {
            cache.evict(dedupKey);
        }
    }
}
