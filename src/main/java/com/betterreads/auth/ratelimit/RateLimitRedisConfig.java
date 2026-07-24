package com.betterreads.auth.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Bucket4j proxy manager that keeps rate-limit buckets in Redis, so every app replica
 * shares one count per client.
 *
 * <p>Bucket state is bytes, so the proxy manager gets its own Lettuce connection with a
 * {@code String}-key, {@code byte[]}-value codec. Buckets expire once enough time has passed to
 * refill them to full, so idle keys clear themselves.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitRedisConfig {

    @Bean(destroyMethod = "shutdown")
    RedisClient rateLimitRedisClient(final DataRedisConnectionDetails redis) {
        final DataRedisConnectionDetails.Standalone standalone = redis.getStandalone();
        if (standalone == null) {
            throw new IllegalStateException(
                "rate limiting requires a standalone Redis; cluster and sentinel are not configured");
        }
        final RedisURI.Builder uri = RedisURI.builder()
            .withHost(standalone.getHost())
            .withPort(standalone.getPort());
        final String password = redis.getPassword();
        if (password != null && !password.isBlank()) {
            final String username = redis.getUsername();
            if (username != null && !username.isBlank()) {
                uri.withAuthentication(username, password.toCharArray());
            } else {
                uri.withPassword(password.toCharArray());
            }
        }
        return RedisClient.create(uri.build());
    }

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(final RedisClient client) {
        final RedisCodec<String, byte[]> codec =
            RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return client.connect(codec);
    }

    @Bean
    ProxyManager<String> rateLimitProxyManager(
        final StatefulRedisConnection<String, byte[]> connection,
        final RateLimitProperties properties
    ) {
        return Bucket4jLettuce.casBasedBuilder(connection)
            .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                Duration.ofSeconds(properties.bucketTtlSeconds())))
            .build();
    }
}
