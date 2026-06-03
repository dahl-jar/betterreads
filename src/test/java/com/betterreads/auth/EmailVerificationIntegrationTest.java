package com.betterreads.auth;

import com.betterreads.auth.emailverification.EmailVerificationService;
import com.betterreads.auth.token.EmailToken;
import com.betterreads.auth.token.EmailTokenRepository;
import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.auth.refresh.RefreshTokenRepository;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.mail.outbox.MailOutbox;
import com.betterreads.mail.outbox.MailOutboxRepository;
import com.betterreads.mail.outbox.MailOutboxService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the email-verification flow end-to-end: registration enqueues a verification mail,
 * the token flips {@code email_verified_at} when consumed, the same token is idempotent under
 * replay, expired and unknown tokens are rejected, and the resend path is enumeration-resistant
 * for both unknown and already-verified addresses.
 *
 * <p>The mail-outbox worker is disabled in this test ({@code mail.outbox.worker-enabled=false})
 * so enqueued rows stay in the DB and the test can read the plaintext token from the payload
 * without racing against a real send.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=120",
    "jwt.refresh-expiration-days=30",
    "auth.refresh-cookie.secure=true",
    "auth.rate-limit.resend-verification-capacity=1000",
    "auth.rate-limit.resend-verification-refill-tokens=1000",
    "auth.rate-limit.resend-verification-refill-seconds=1",
    "auth.rate-limit.verify-email-capacity=1000",
    "auth.rate-limit.verify-email-refill-tokens=1000",
    "auth.rate-limit.verify-email-refill-seconds=1",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false"
})
@SuppressWarnings("PMD.ExcessiveImports")
class EmailVerificationIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";

    private static final String VERIFY_URL = "/api/v1/auth/verify-email";

    private static final String RESEND_URL = "/api/v1/auth/resend-verification";

    private static final String USERNAME = "alice";

    private static final String EMAIL = "alice@example.com";

    private static final String PASSWORD = "Str0ngPassword!";

    private static final String FIELD_TOKEN = "token";

    private static final String FIELD_EMAIL = "email";

    private static final String FIELD_USERNAME = "username";

    private static final String FIELD_PASSWORD = "password";

    private static final int CONCURRENT_THREADS = 16;

    private static final int CONCURRENT_TIMEOUT_SECONDS = 10;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailTokenRepository emailTokenRepository;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        void enqueuesVerificationMailAndLeavesUserUnverified() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerPayload(USERNAME, EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.emailVerified").value(false));

            assertThat(userRepository.findByEmail(EMAIL))
                .as("registration leaves email_verified_at null until the link is clicked")
                .isPresent()
                .get()
                .satisfies(u -> assertThat(u.getEmailVerifiedAt()).isNull());

            assertThat(mailOutboxRepository.findAll())
                .as("registration enqueues exactly one verification mail addressed to the user")
                .hasSize(1)
                .first()
                .satisfies(row -> assertThat(row.getRecipient()).isEqualTo(EMAIL))
                .satisfies(row -> assertThat(row.getTemplate())
                    .isEqualTo(MailOutboxService.TEMPLATE_EMAIL_VERIFICATION))
                .satisfies(row -> assertThat(payloadField(row, FIELD_TOKEN)).isNotBlank());

            assertThat(emailTokenRepository.findAll())
                .as("exactly one unconsumed verification token persisted for the new user")
                .hasSize(1)
                .first()
                .satisfies(t -> assertThat(t.getConsumedAt()).isNull());
        }
    }

    @Nested
    @DisplayName("POST /auth/verify-email")
    class VerifyEmail {

        @Test
        void flipsEmailVerifiedAtAndConsumesTheToken() throws Exception {
            registerNewUser();
            final String token = readEnqueuedToken();
            final long userId = userRepository.findByEmail(EMAIL).orElseThrow().getUserId();

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(token)))
                .andExpect(status().isNoContent());

            assertThat(userRepository.findById(userId).orElseThrow().getEmailVerifiedAt())
                .as("verify must set email_verified_at on the user")
                .isNotNull();

            assertThat(emailTokenRepository.findActive(
                    userId, EmailToken.Purpose.EMAIL_VERIFICATION))
                .as("no unconsumed token remains after verify")
                .isEmpty();
        }

        @Test
        void replayingTheSameTokenStillReturns204AndLeavesUserVerifiedOnce() throws Exception {
            registerNewUser();
            final long userId = userRepository.findByEmail(EMAIL).orElseThrow().getUserId();
            final String token = readEnqueuedToken();

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(token)))
                .andExpect(status().isNoContent());

            final Instant verifiedAtAfterFirst =
                userRepository.findById(userId).orElseThrow().getEmailVerifiedAt();
            assertThat(verifiedAtAfterFirst).isNotNull();

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(token)))
                .andExpect(status().isNoContent());

            assertThat(userRepository.findById(userId).orElseThrow().getEmailVerifiedAt())
                .as("replay must not move the verified timestamp")
                .isEqualTo(verifiedAtAfterFirst);
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            registerNewUser();
            final long userId = userRepository.findByEmail(EMAIL).orElseThrow().getUserId();
            final String token = readEnqueuedToken();
            expireTokens(userId);

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(token)))
                .andExpect(status().isBadRequest());

            assertThat(userRepository.findById(userId).orElseThrow().getEmailVerifiedAt())
                .as("expired token must not flip the verified flag")
                .isNull();
        }

        @Test
        void rejectsUnknownToken() throws Exception {
            registerNewUser();

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload("not-a-real-token")))
                .andExpect(status().isBadRequest());
        }

        /**
         * If a user requests a resend before clicking the original link, the prior token is
         * marked consumed by the issue path. Presenting that superseded token must NOT report
         * success: the user is still unverified, and a 204 here would leave the frontend
         * showing a verified state while {@code email_verified_at} stays null. Only tokens
         * consumed by an actual verification (i.e. the user IS verified) qualify for the
         * idempotent-replay 204 branch.
         */
        @Test
        void rejectsSupersededTokenWhenUserStillUnverified() throws Exception {
            registerNewUser();
            final long userId = userRepository.findByEmail(EMAIL).orElseThrow().getUserId();
            final String firstToken = readEnqueuedToken();
            mailOutboxRepository.deleteAll();
            emailVerificationService.requestResend(EMAIL);

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(firstToken)))
                .andExpect(status().isBadRequest());

            assertThat(userRepository.findById(userId).orElseThrow().getEmailVerifiedAt())
                .as("superseded token must leave the verified flag null")
                .isNull();
        }

        /**
         * Two concurrent requests presenting the same valid token must both finish without a 500
         * and leave the user verified exactly once. The pessimistic-write lock on the token row
         * serializes the consume; the second request finds {@code consumed_at != null} and
         * returns 204 silently per the idempotency contract.
         */
        @SuppressWarnings("PMD.DoNotUseThreads")
        @Test
        void concurrentVerifyOnSameTokenIsSerializedAndIdempotent() throws Exception {
            registerNewUser();
            final long userId = userRepository.findByEmail(EMAIL).orElseThrow().getUserId();
            final String token = readEnqueuedToken();

            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<?>> futures = new ArrayList<>(CONCURRENT_THREADS);

            try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS)) {
                for (int i = 0; i < CONCURRENT_THREADS; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        emailVerificationService.verify(token);
                        return null;
                    }));
                }
                start.countDown();
                for (final Future<?> future : futures) {
                    future.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            }

            assertThat(userRepository.findById(userId).orElseThrow().getEmailVerifiedAt())
                .as("user is verified exactly once even under concurrent verify")
                .isNotNull();
            assertThat(emailTokenRepository.findActive(
                    userId, EmailToken.Purpose.EMAIL_VERIFICATION))
                .as("the single token row is consumed; no duplicates linger")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("POST /auth/resend-verification")
    class ResendVerification {

        @Test
        void issuesNewTokenAndConsumesPriorOutstandingForUnverifiedUser() throws Exception {
            registerNewUser();
            final long userId = userRepository.findByEmail(EMAIL).orElseThrow().getUserId();
            final String firstToken = readEnqueuedToken();
            mailOutboxRepository.deleteAll();

            mockMvc.perform(post(RESEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resendPayload(EMAIL)))
                .andExpect(status().isNoContent());

            assertThat(emailTokenRepository.findActive(
                    userId, EmailToken.Purpose.EMAIL_VERIFICATION))
                .as("resend leaves exactly one unconsumed token; the prior is consumed by the issue path")
                .hasSize(1);
            assertThat(mailOutboxRepository.findAll())
                .as("resend enqueues a fresh verification mail")
                .hasSize(1)
                .first()
                .satisfies(row -> assertThat(row.getRecipient()).isEqualTo(EMAIL))
                .satisfies(row -> assertThat(payloadField(row, FIELD_TOKEN))
                    .as("the new token must differ from the previously-issued one")
                    .isNotEqualTo(firstToken));
        }

        @Test
        void returnsNoContentForUnknownEmailWithoutEnqueueing() throws Exception {
            mockMvc.perform(post(RESEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resendPayload("ghost@example.com")))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll())
                .as("resend must not leak account existence by writing an outbox row for an unknown email")
                .isEmpty();
            assertThat(emailTokenRepository.findAll())
                .as("resend must not create token rows for an unknown email")
                .isEmpty();
        }

        @Test
        void returnsNoContentForAlreadyVerifiedUserWithoutEnqueueing() throws Exception {
            registerNewUser();
            final long userId = userRepository.findByEmail(EMAIL).orElseThrow().getUserId();
            final String token = readEnqueuedToken();
            emailVerificationService.verify(token);
            mailOutboxRepository.deleteAll();

            mockMvc.perform(post(RESEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resendPayload(EMAIL)))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll())
                .as("already-verified users must not trigger another verification email")
                .isEmpty();
            assertThat(emailTokenRepository.findActive(
                    userId, EmailToken.Purpose.EMAIL_VERIFICATION))
                .as("already-verified users must not get a fresh active token")
                .isEmpty();
        }

        @Test
        void normalizesEmailCasingBeforeLookup() throws Exception {
            registerNewUser();
            mailOutboxRepository.deleteAll();

            mockMvc.perform(post(RESEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resendPayload("Alice@Example.COM")))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll())
                .as("upper-case email matches the normalized stored value")
                .hasSize(1);
        }

        /**
         * Two concurrent resend calls race on the partial unique index
         * {@code (user_id) WHERE consumed_at IS NULL}. The loser must catch the
         * {@link org.springframework.dao.DataIntegrityViolationException} and return silently;
         * at most one fresh active token may survive.
         */
        @SuppressWarnings("PMD.DoNotUseThreads")
        @Test
        void concurrentResendLeavesAtMostOneActiveToken() throws Exception {
            registerNewUser();
            final long userId = userRepository.findByEmail(EMAIL).orElseThrow().getUserId();

            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<?>> futures = new ArrayList<>(CONCURRENT_THREADS);

            try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS)) {
                for (int i = 0; i < CONCURRENT_THREADS; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        emailVerificationService.requestResend(EMAIL);
                        return null;
                    }));
                }
                start.countDown();
                for (final Future<?> future : futures) {
                    future.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            }

            assertThat(emailTokenRepository.findActive(
                    userId, EmailToken.Purpose.EMAIL_VERIFICATION))
                .as("partial unique index keeps at most one active token under concurrent resend")
                .hasSizeLessThanOrEqualTo(1);
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void registerNewUser() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(USERNAME, EMAIL, PASSWORD)))
            .andExpect(status().isCreated());
    }

    private void expireTokens(final long userId) {
        emailTokenRepository.findActive(
            userId, EmailToken.Purpose.EMAIL_VERIFICATION).forEach(t -> {
                final Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
                t.setIssuedAt(past);
                t.setExpiresAt(past.plusSeconds(1));
                emailTokenRepository.save(t);
            });
    }

    private String readEnqueuedToken() {
        final List<MailOutbox> rows = mailOutboxRepository.findAll();
        if (rows.isEmpty()) {
            throw new IllegalStateException("expected an enqueued verification row but found none");
        }
        return payloadField(rows.get(0), FIELD_TOKEN);
    }

    private String payloadField(final MailOutbox row, final String field) {
        try {
            final JsonNode node = objectMapper.readTree(row.getPayload());
            return node.path(field).asString();
        } catch (final JacksonException ex) {
            throw new IllegalStateException("malformed outbox payload", ex);
        }
    }

    private String registerPayload(final String username, final String email, final String password) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_USERNAME, username);
        node.put(FIELD_EMAIL, email);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }

    private String verifyPayload(final String token) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_TOKEN, token);
        return objectMapper.writeValueAsString(node);
    }

    private String resendPayload(final String email) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_EMAIL, email);
        return objectMapper.writeValueAsString(node);
    }
}
