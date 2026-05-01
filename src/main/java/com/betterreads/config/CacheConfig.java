package com.betterreads.config;

import java.time.Duration;
import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final long SEARCH_TTL_MINUTES = 15L;

    private static final long DETAIL_TTL_MINUTES = 60L;

    private static final long SEARCH_MAX_ENTRIES = 500L;

    private static final long BOOK_DETAIL_MAX_ENTRIES = 1000L;

    private static final long AUTHOR_DETAIL_MAX_ENTRIES = 500L;

    @Bean
    CacheManager cacheManager() {
        final CaffeineCache searchResults = buildCache(
            "searchResults", SEARCH_TTL_MINUTES, SEARCH_MAX_ENTRIES);
        final CaffeineCache bookDetails = buildCache(
            "bookDetails", DETAIL_TTL_MINUTES, BOOK_DETAIL_MAX_ENTRIES);
        final CaffeineCache authorDetails = buildCache(
            "authorDetails", DETAIL_TTL_MINUTES, AUTHOR_DETAIL_MAX_ENTRIES);

        final SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(searchResults, bookDetails, authorDetails));
        return manager;
    }

    private CaffeineCache buildCache(
            final String name, final long ttlMinutes, final long maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
            .maximumSize(maxSize)
            .build());
    }
}
