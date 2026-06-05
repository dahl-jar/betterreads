package com.betterreads.auth;

import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code X-Forwarded-For} IS honored when the request comes from a trusted-proxy
 * CIDR. The default {@link AuthIntegrationTest} runs with no trusted proxies; this class boots
 * a separate context that trusts loopback so MockMvc's {@code 127.0.0.1} client is treated as a
 * proxy. Each fake client IP in the header should get its own bucket.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=60",
    "jwt.refresh-expiration-days=30",
    "auth.rate-limit.trusted-proxies=127.0.0.1/32"
})
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class RateLimitTrustedProxyTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final int LOGIN_RATE_LIMIT_BURST = 10;

    private static final String XFF_HEADER = "X-Forwarded-For";

    private static final String CLIENT_A = "203.0.113.10";

    private static final String CLIENT_B = "203.0.113.20";

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

    @Test
    void honorsForwardedForFromTrustedProxyAndKeepsBucketsPerClient() throws Exception {
        final String body = loginPayload();

        for (int i = 0; i < LOGIN_RATE_LIMIT_BURST; i++) {
            mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(XFF_HEADER, CLIENT_A));
        }

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(XFF_HEADER, CLIENT_A))
            .andExpect(status().isTooManyRequests());

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(XFF_HEADER, CLIENT_B))
            .andExpect(status().isUnauthorized());
    }

    private String loginPayload() {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("identifier", "ghost");
        node.put("password", "anything-not-matching");
        return objectMapper.writeValueAsString(node);
    }
}
