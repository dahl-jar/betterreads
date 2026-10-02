package com.betterreads.ratelimit;

import com.betterreads.testsupport.ContainerizedTest;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static com.betterreads.ratelimit.RateLimitFixtures.assertSeparateBucketsPerClient;
import static com.betterreads.ratelimit.RateLimitFixtures.loginPayload;

abstract class RateLimitBucketTest extends ContainerizedTest {

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

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected void assertSeparateBucketsPer(final String clientIpHeader) throws Exception {
        final String body = loginPayload(objectMapper);

        assertSeparateBucketsPerClient(mockMvc, body, clientIpHeader);
    }
}
