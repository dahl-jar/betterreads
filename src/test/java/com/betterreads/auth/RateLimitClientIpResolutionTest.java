package com.betterreads.auth;

import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the rate limiter keys buckets on {@code CF-Connecting-IP} when present.
 * {@code CF-Connecting-IP} is set by Cloudflare and overwritten on every request, while
 * {@code X-Forwarded-For} can be appended to by the client before reaching Cloudflare and is
 * therefore unsafe as a bucket key. Production runs behind Cloudflare Tunnel; trusting
 * {@code CF-Connecting-IP} closes the bypass where a fresh forged XFF per request gives the
 * caller a fresh bucket per request.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=60",
    "jwt.refresh-expiration-days=30"
})
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class RateLimitClientIpResolutionTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final int LOGIN_RATE_LIMIT_BURST = 10;

    private static final String CF_CONNECTING_IP_HEADER = "CF-Connecting-IP";

    private static final String CLIENT_A = "203.0.113.10";

    private static final String CLIENT_B = "203.0.113.20";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .addFilter(springSecurityFilterChain)
            .build();
        rateLimitFilter.reset();
    }

    /**
     * A different {@code CF-Connecting-IP} gets its own bucket so a real client behind
     * Cloudflare cannot be rate-limited by another client's burst. Pre-fix, both
     * {@code CF-Connecting-IP} values mapped to the same {@code 127.0.0.1} bucket and the
     * second client received 429 instead of 401, so this test fails on broken code and passes
     * once {@code clientIp()} reads the header.
     */
    @Test
    void differentCfConnectingIpsKeepSeparateBuckets() throws Exception {
        final String body = loginPayload();

        for (int i = 0; i < LOGIN_RATE_LIMIT_BURST; i++) {
            mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(CF_CONNECTING_IP_HEADER, CLIENT_A))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(CF_CONNECTING_IP_HEADER, CLIENT_A))
            .andExpect(status().isTooManyRequests());

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(CF_CONNECTING_IP_HEADER, CLIENT_B))
            .andExpect(status().isUnauthorized());
    }

    private String loginPayload() throws JsonProcessingException {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("identifier", "ghost");
        node.put("password", "anything-not-matching");
        return objectMapper.writeValueAsString(node);
    }
}
