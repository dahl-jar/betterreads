package com.betterreads.config;

import java.time.Duration;
import java.util.Set;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import tools.jackson.databind.ObjectMapper;

/**
 * Two caches with different homes: promoted book detail in Redis, and a short-lived in-process cache
 * for search results.
 *
 * <p>Book detail is Redis-backed so every replica shares it; it is written once at promotion and
 * evicted on re-enrichment, with a TTL backstop and the cluster {@code allkeys-lru} cap bounding
 * growth. Search results stay in Caffeine with a short TTL so a burst of identical queries reuses one
 * Meilisearch call without holding volatile result lists in Redis.
 */
@Configuration
@EnableCaching
@ConfigurationProperties(prefix = "betterreads.cache")
public class CacheConfig {

    private static final long DEFAULT_TTL_HOURS = 24L;

    private static final long SEARCH_MAX_ENTRIES = 1_000L;

    private static final long DEFAULT_SEARCH_TTL_SECONDS = 10L;

    static final String BOOK_DETAILS = "bookDetails";

    static final String SEARCH_RESULTS = "searchResults";

    private Duration bookDetailTtl = Duration.ofHours(DEFAULT_TTL_HOURS);

    private Duration searchResultTtl = Duration.ofSeconds(DEFAULT_SEARCH_TTL_SECONDS);

    /** Builds the {@code bookDetails} cache, storing each detail response as JSON under the TTL. */
    @Bean
    @Primary
    RedisCacheManager bookDetailCacheManager(
        final RedisConnectionFactory connectionFactory,
        final ObjectMapper objectMapper
    ) {
        final RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(bookDetailTtl)
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(objectMapper, BookDetailResponse.class)));
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .initialCacheNames(Set.of(BOOK_DETAILS))
            .build();
    }

    /** Builds the in-process {@code searchResults} cache that collapses rapid identical queries. */
    @Bean
    CacheManager searchCacheManager() {
        final CaffeineCacheManager manager = new CaffeineCacheManager(SEARCH_RESULTS);
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(searchResultTtl)
            .maximumSize(SEARCH_MAX_ENTRIES));
        return manager;
    }

    public void setBookDetailTtl(final Duration bookDetailTtl) {
        this.bookDetailTtl = bookDetailTtl;
    }

    public void setSearchResultTtl(final Duration searchResultTtl) {
        this.searchResultTtl = searchResultTtl;
    }
}
