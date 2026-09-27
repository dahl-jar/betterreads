package com.betterreads.config;

import java.time.Duration;
import java.util.Set;

import com.betterreads.book.BookDetailCache;
import com.betterreads.features.bookdetail.BookDetailResponse;
import com.betterreads.features.search.SearchResultsCache;
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

/** Caches book detail in Redis so replicas share it, and search results in process. */
@Configuration
@EnableCaching
@ConfigurationProperties(prefix = "betterreads.cache")
class CacheConfig {

    private static final long DEFAULT_BOOK_DETAIL_TTL_HOURS = 24L;

    private static final long SEARCH_MAX_ENTRIES = 1_000L;

    private static final long DEFAULT_SEARCH_TTL_SECONDS = 10L;

    private Duration bookDetailTtl = Duration.ofHours(DEFAULT_BOOK_DETAIL_TTL_HOURS);

    private Duration searchResultTtl = Duration.ofSeconds(DEFAULT_SEARCH_TTL_SECONDS);

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
            .initialCacheNames(Set.of(BookDetailCache.NAME))
            .build();
    }

    @Bean
    CacheManager searchCacheManager() {
        final CaffeineCacheManager manager = new CaffeineCacheManager(SearchResultsCache.NAME);
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
