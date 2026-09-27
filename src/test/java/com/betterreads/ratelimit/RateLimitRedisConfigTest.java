package com.betterreads.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.RedisURI;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RateLimitRedisConfigTest {

    private static final String USERNAME = "darrow";

    private static final String PASSWORD = "howler";

    private static final String HOST = "localhost";

    private static final int PORT = 6379;

    static Stream<Arguments> credentials() {
        return Stream.of(
            Arguments.of("should authenticate as configured user", USERNAME, PASSWORD),
            Arguments.of("should send password alone if no username", null, PASSWORD),
            Arguments.of("should send no credentials if no password", null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("credentials")
    void shouldSendConfiguredCredentials(
        final String description,
        @Nullable final String username,
        @Nullable final String password
    ) {
        final DataRedisConnectionDetails redis = new DataRedisConnectionDetails() {

            @Override
            public @Nullable String getUsername() {
                return username;
            }

            @Override
            public @Nullable String getPassword() {
                return password;
            }

            @Override
            public Standalone getStandalone() {
                return Standalone.of(HOST, PORT);
            }
        };

        final RedisURI uri = RateLimitRedisConfig.redisUri(redis);

        final RedisCredentialsProvider provider = uri.getCredentialsProvider();
        final RedisCredentials credentials = provider.resolveCredentials().block();
        assertThat(credentials)
            .as(description)
            .extracting(RedisCredentials::getUsername, RateLimitRedisConfigTest::passwordOf)
            .containsExactly(username, password);
    }

    private static @Nullable String passwordOf(final RedisCredentials credentials) {
        final char[] password = credentials.getPassword();
        return password == null ? null : String.valueOf(password);
    }
}
