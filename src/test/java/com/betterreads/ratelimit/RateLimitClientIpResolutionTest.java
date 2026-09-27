package com.betterreads.ratelimit;

import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.ratelimit.RateLimitFixtures.CF_CONNECTING_IP_HEADER;
import static com.betterreads.ratelimit.RateLimitFixtures.assertSeparateBucketsPerClient;
import static com.betterreads.ratelimit.RateLimitFixtures.loginPayload;

/**
 * Cloudflare overwrites {@code CF-Connecting-IP} on every request, while a client can append
 * to {@code X-Forwarded-For} before the request reaches Cloudflare. Keying on the forgeable
 * header would hand a caller a fresh bucket per request.
 */
@SpringBootTest
@Testcontainers
class RateLimitClientIpResolutionTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        rateLimitFilter.reset();
    }

    /**
     * Each {@code CF-Connecting-IP} value gets its own bucket, so one client's burst cannot
     * rate-limit another. Without the header lookup both values collapse onto the
     * {@code 127.0.0.1} bucket and the second client is throttled on its first request.
     */
    @Test
    void differentCfConnectingIpsKeepSeparateBuckets() throws Exception {
        final String body = loginPayload(objectMapper);

        assertSeparateBucketsPerClient(mockMvc, body, CF_CONNECTING_IP_HEADER);
    }
}
