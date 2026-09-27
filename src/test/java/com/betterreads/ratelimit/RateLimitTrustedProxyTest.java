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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.ratelimit.RateLimitFixtures.XFF_HEADER;
import static com.betterreads.ratelimit.RateLimitFixtures.assertSeparateBucketsPerClient;
import static com.betterreads.ratelimit.RateLimitFixtures.loginPayload;

/**
 * Trusting loopback makes MockMvc's {@code 127.0.0.1} client count as a proxy, so each client
 * IP in the header gets its own bucket.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.rate-limit.trusted-proxies=127.0.0.1/32"
})
class RateLimitTrustedProxyTest extends ContainerizedTest {

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

    @Test
    void forwardedForFromATrustedProxyKeepsBucketsPerClient() throws Exception {
        final String body = loginPayload(objectMapper);

        assertSeparateBucketsPerClient(mockMvc, body, XFF_HEADER);
    }
}
