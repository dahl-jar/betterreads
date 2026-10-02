package com.betterreads.ratelimit;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.ratelimit.RateLimitFixtures.XFF_HEADER;

/**
 * Trusting loopback makes MockMvc's {@code 127.0.0.1} client count as a proxy, so each client
 * IP in the header gets its own bucket.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.rate-limit.trusted-proxies=127.0.0.1/32"
})
class RateLimitTrustedProxyTest extends RateLimitBucketTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Test
    void forwardedForFromATrustedProxyKeepsBucketsPerClient() throws Exception {
        assertSeparateBucketsPer(XFF_HEADER);
    }
}
