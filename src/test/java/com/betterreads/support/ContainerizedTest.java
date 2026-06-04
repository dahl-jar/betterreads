package com.betterreads.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests that boot the full application context, providing the shared Redis the
 * context needs to start.
 *
 * <p>Redis is one singleton for the whole suite because each test clears its own bucket keys through
 * {@code RateLimitFilter.reset()}, so sharing the instance stays isolated. Each test still declares
 * its own Postgres container so a class gets a clean schema.
 */
// PMD.AbstractClassWithoutAbstractMethod: a shared-fixture base, not a template-method type.
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class ContainerizedTest {

    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }
}
