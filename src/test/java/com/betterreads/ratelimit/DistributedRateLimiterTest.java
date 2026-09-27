package com.betterreads.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.testsupport.ContainerizedTest;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class DistributedRateLimiterTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int PERMITS_PER_MINUTE = 2;

    private static final Duration MAX_WAIT = Duration.ofMillis(1);

    @Autowired
    private ProxyManager<String> proxyManager;

    @Test
    void shouldDenyPermitPastPerMinuteLimit() {
        final RateLimiter limiter =
            new DistributedRateLimiter(proxyManager, UUID.randomUUID().toString(), PERMITS_PER_MINUTE);

        final List<Boolean> acquired = Stream.generate(() -> limiter.tryAcquire(MAX_WAIT))
            .limit(PERMITS_PER_MINUTE + 1)
            .toList();

        assertThat(acquired).containsExactly(true, true, false);
    }
}
