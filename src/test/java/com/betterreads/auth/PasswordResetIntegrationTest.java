package com.betterreads.auth;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.passwordreset.PasswordResetService;
import com.betterreads.auth.token.EmailToken;
import com.betterreads.auth.token.EmailTokenRepository;
import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.auth.refresh.RefreshTokenRepository;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.mail.outbox.MailOutbox;
import com.betterreads.mail.outbox.MailOutboxRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.Cookie;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the password-reset flow end-to-end: token issue, single-use consumption, expiry,
 * enumeration-resistance, and the side-effect that all refresh tokens are revoked when a reset
 * succeeds. The plaintext token is captured through a test {@link PasswordResetMailer} so the
 * test never reads it from the DB.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=120",
    "jwt.refresh-expiration-days=30",
    "auth.refresh-cookie.secure=true",
    "auth.rate-limit.forgot-password-capacity=1000",
    "auth.rate-limit.forgot-password-refill-tokens=1000",
    "auth.rate-limit.forgot-password-refill-seconds=1",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false"
})
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.CouplingBetweenObjects"})
class PasswordResetIntegrationTest {

    private static final String FORGOT_URL = "/api/v1/auth/forgot-password";

    private static final String RESET_URL = "/api/v1/auth/reset-password";

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final String REFRESH_URL = "/api/v1/auth/refresh";

    private static final String COOKIE_NAME = "br_refresh";

    private static final String SET_COOKIE_HEADER = "Set-Cookie";

    private static final String USERNAME = "alice";

    private static final String EMAIL = "alice@example.com";

    private static final String OLD_PASSWORD = "OldP4ssword!";

    private static final String NEW_PASSWORD = "BrandN3wPass!";

    private static final String FIELD_EMAIL = "email";

    private static final String FIELD_TOKEN = "token";

    private static final String FIELD_NEW_PASSWORD = "newPassword";

    private static final String FIELD_IDENTIFIER = "identifier";

    private static final String FIELD_PASSWORD = "password";

    private static final int CONCURRENT_THREADS = 16;

    private static final int CONCURRENT_TIMEOUT_SECONDS = 10;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailTokenRepository emailTokenRepository;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
        mailOutboxRepository.deleteAll();
        emailTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        rateLimitFilter.reset();
    }

    @Nested
    @DisplayName("POST /auth/forgot-password")
    class ForgotPassword {

        @Test
        void enqueuesOutboxRowForKnownEmail() throws Exception {
            seedUser();

            mockMvc.perform(post(FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(forgotPayload(EMAIL)))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll())
                .as("exactly one outbox row enqueued for the matching account, addressed to the original email")
                .hasSize(1)
                .first()
                .satisfies(row -> assertThat(row.getRecipient()).isEqualTo(EMAIL))
                .satisfies(row -> assertThat(row.getTemplate()).isEqualTo("password_reset"))
                .satisfies(row -> assertThat(payloadField(row, FIELD_TOKEN)).isNotBlank())
                .satisfies(row -> assertThat(row.getSentAt()).isNull())
                .satisfies(row -> assertThat(row.getFailedAt()).isNull());
        }

        @Test
        void returnsNoContentForUnknownEmailWithoutEnqueueing() throws Exception {
            mockMvc.perform(post(FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(forgotPayload("ghost@example.com")))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll())
                .as("must not leak account existence by writing an outbox row for an unknown email")
                .isEmpty();
        }

        @Test
        void normalizesEmailCasingBeforeLookup() throws Exception {
            seedUser();

            mockMvc.perform(post(FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(forgotPayload("Alice@Example.COM")))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll()).hasSize(1);
        }

        @Test
        void rejectsMalformedEmail() throws Exception {
            mockMvc.perform(post(FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(forgotPayload("not-an-email")))
                .andExpect(status().isBadRequest());

            assertThat(mailOutboxRepository.findAll()).isEmpty();
        }

        /**
         * Hammers the service from many threads at once. Two transactions racing on the partial
         * unique index for {@code (user_id) WHERE consumed_at IS NULL} produce a
         * {@link org.springframework.dao.DataIntegrityViolationException}; the loser must catch
         * that, not surface a 500, and must leave at most one active token in the DB.
         *
         * <p>The service is invoked directly so the per-IP rate limit filter is bypassed; the
         * unique-constraint race is the unit under test, not the throttle.
         */
        // PMD.DoNotUseThreads is a J2EE-webapp rule; this test needs real threads for the race.
        @SuppressWarnings("PMD.DoNotUseThreads")
        @Test
        void concurrentRequestsLeaveAtMostOneActiveToken() throws Exception {
            seedUser();
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<?>> futures = new ArrayList<>(CONCURRENT_THREADS);

            try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS)) {
                for (int i = 0; i < CONCURRENT_THREADS; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        passwordResetService.requestReset(EMAIL);
                        return null;
                    }));
                }
                start.countDown();
                for (final Future<?> future : futures) {
                    future.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            }

            assertThat(emailTokenRepository.findActive(
                    userRepository.findByEmail(EMAIL).orElseThrow().getUserId(),
                    EmailToken.Purpose.PASSWORD_RESET))
                .as("at most one active token survives the race; loser caught DataIntegrityViolation")
                .hasSizeLessThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("POST /auth/reset-password")
    class ResetPassword {

        @Test
        void resetsPasswordAndRevokesAllRefreshTokens() throws Exception {
            final long userId = seedUser();
            final String oldRefreshCookie = loginAndExtractCookieValue(OLD_PASSWORD);
            requestForgotPassword();
            final String token = readEnqueuedToken();

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload(token, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

            assertThat(activeRefreshTokenCount(userId))
                .as("successful reset revokes every refresh token so other devices are kicked off")
                .isZero();

            mockMvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE_NAME, oldRefreshCookie)))
                .andExpect(status().isUnauthorized());

            mockMvc.perform(post(LOGIN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload(USERNAME, OLD_PASSWORD)))
                .andExpect(status().isUnauthorized());

            mockMvc.perform(post(LOGIN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload(USERNAME, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(COOKIE_NAME));
        }

        @Test
        void rejectsAlreadyConsumedToken() throws Exception {
            seedUser();
            requestForgotPassword();
            final String token = readEnqueuedToken();

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload(token, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload(token, "AnotherPassw0rd!")))
                .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            final long userId = seedUser();
            requestForgotPassword();
            final String token = readEnqueuedToken();
            expirePasswordResetTokens(userId);

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload(token, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsUnknownToken() throws Exception {
            seedUser();

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload("not-a-real-token", NEW_PASSWORD)))
                .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsWeakPassword() throws Exception {
            seedUser();
            requestForgotPassword();
            final String token = readEnqueuedToken();

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload(token, "abc")))
                .andExpect(status().isBadRequest());
        }
    }

    private long seedUser() {
        final User user = new User();
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        return userRepository.save(user).getUserId();
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private String loginAndExtractCookieValue(final String password) throws Exception {
        final MvcResult result = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(USERNAME, password)))
            .andExpect(status().isOk())
            .andReturn();
        final String setCookie = result.getResponse().getHeader(SET_COOKIE_HEADER);
        if (setCookie == null) {
            throw new IllegalStateException("login response is missing the Set-Cookie header");
        }
        final int equals = setCookie.indexOf('=');
        final int semi = setCookie.indexOf(';');
        return setCookie.substring(equals + 1, semi);
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void requestForgotPassword() throws Exception {
        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotPayload(EMAIL)))
            .andExpect(status().isNoContent());
    }

    private long activeRefreshTokenCount(final long userId) {
        return refreshTokenRepository.findAll().stream()
            .filter(rt -> rt.getUserId() == userId)
            .filter(rt -> rt.getRevokedAt() == null)
            .count();
    }

    private void expirePasswordResetTokens(final long userId) {
        emailTokenRepository.findActive(
            userId, EmailToken.Purpose.PASSWORD_RESET).forEach(t -> {
                final Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
                t.setIssuedAt(past);
                t.setExpiresAt(past.plusSeconds(1));
                emailTokenRepository.save(t);
            });
    }

    private String forgotPayload(final String email) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_EMAIL, email);
        return objectMapper.writeValueAsString(node);
    }

    private String resetPayload(final String token, final String newPassword) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_TOKEN, token);
        node.put(FIELD_NEW_PASSWORD, newPassword);
        return objectMapper.writeValueAsString(node);
    }

    private String loginPayload(final String identifier, final String password) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_IDENTIFIER, identifier);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }

    private String readEnqueuedToken() {
        final List<MailOutbox> rows = mailOutboxRepository.findAll();
        if (rows.isEmpty()) {
            throw new IllegalStateException("expected an enqueued password-reset row but found none");
        }
        return payloadField(rows.get(0), FIELD_TOKEN);
    }

    private String payloadField(final MailOutbox row, final String field) {
        try {
            final JsonNode node = objectMapper.readTree(row.getPayload());
            return node.path(field).asText();
        } catch (final JacksonException ex) {
            throw new IllegalStateException("malformed outbox payload", ex);
        }
    }
}
