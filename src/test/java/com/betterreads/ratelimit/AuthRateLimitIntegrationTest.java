package com.betterreads.ratelimit;

import com.betterreads.testsupport.Accounts;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.ratelimit.RateLimitFixtures.LOGIN_BURST;
import static com.betterreads.ratelimit.RateLimitFixtures.LOGIN_URL;
import static com.betterreads.ratelimit.RateLimitFixtures.RETRY_AFTER_HEADER;
import static com.betterreads.ratelimit.RateLimitFixtures.XFF_HEADER;
import static com.betterreads.ratelimit.RateLimitFixtures.loginPayload;
import static com.betterreads.testsupport.Accounts.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "mail.outbox.worker-enabled=false"
})
class AuthRateLimitIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String REGISTER_URL = "/api/v1/auth/register";

    private static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

    private static final String CSP_HEADER = "Content-Security-Policy";

    private static final String NOSNIFF_VALUE = "nosniff";

    private static final int REGISTER_RATE_LIMIT_BURST = 5;

    private static final int OVER_BURST = 5;

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
    void blocksRegisterWithTooManyAttemptsFromSameIp() throws Exception {
        final String userPrefix = "user";
        for (int i = 0; i < REGISTER_RATE_LIMIT_BURST; i++) {
            final String username = userPrefix + i;
            final String body =
                Accounts.registerPayload(objectMapper, username, username + "@example.com", PASSWORD);
            mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body));
        }

        final String overflow =
            Accounts.registerPayload(objectMapper, "overflow", "overflow@example.com", PASSWORD);
        mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(overflow))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(RETRY_AFTER_HEADER));
    }

    @Test
    void doesNotConsumeTokensForNonPostMethods() throws Exception {
        final String body = loginPayload(objectMapper);

        for (int i = 0; i < LOGIN_BURST + OVER_BURST; i++) {
            mockMvc.perform(options(LOGIN_URL));
        }

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rateLimitedResponseStillIncludesSecurityHeaders() throws Exception {
        final String body = loginPayload(objectMapper);
        exhaustLoginBurst(body);

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string(CONTENT_TYPE_OPTIONS_HEADER, NOSNIFF_VALUE))
            .andExpect(header().exists(CSP_HEADER));
    }

    @Test
    void retryAfterHeaderIsAtLeastOneSecond() throws Exception {
        final String body = loginPayload(objectMapper);
        exhaustLoginBurst(body);

        final MvcResult rateLimitedResult = mockMvc.perform(
                post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests())
            .andReturn();
        final MockHttpServletResponse rateLimitedResponse = rateLimitedResult.getResponse();
        final String retryAfter = rateLimitedResponse.getHeader(RETRY_AFTER_HEADER);

        assertThat(retryAfter)
            .isNotNull()
            .satisfies(value -> assertThat(Long.parseLong(value)).isGreaterThanOrEqualTo(1L));
    }

    // PMD.AvoidUsingHardCodedIP: private test addresses vary X-Forwarded-For without network calls.
    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    void doesNotTrustForwardedForFromUntrustedClient() throws Exception {
        final String body = loginPayload(objectMapper);

        for (int i = 0; i < LOGIN_BURST; i++) {
            mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(XFF_HEADER, "10.0.0." + i));
        }

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(XFF_HEADER, "10.0.0.99"))
            .andExpect(status().isTooManyRequests());
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void exhaustLoginBurst(final String body) throws Exception {
        for (int i = 0; i < LOGIN_BURST; i++) {
            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body));
        }
    }
}
