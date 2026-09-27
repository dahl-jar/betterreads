package com.betterreads.features.account;

import com.betterreads.features.session.RefreshTokenRepository;
import com.betterreads.mailoutbox.MailOutboxRepository;
import com.betterreads.mailoutbox.MailOutboxService;
import com.betterreads.ratelimit.RateLimitFilter;
import com.betterreads.testsupport.Accounts;
import com.betterreads.testsupport.ContainerizedTest;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.features.account.AccountTestFixture.FIELD_TOKEN;
import static com.betterreads.features.account.AccountTestFixture.UNKNOWN_EMAIL;
import static com.betterreads.features.account.AccountTestFixture.UNKNOWN_TOKEN;
import static com.betterreads.testsupport.Accounts.EMAIL;
import static com.betterreads.testsupport.Accounts.MIXED_CASE_EMAIL;
import static com.betterreads.testsupport.Accounts.PASSWORD;
import static com.betterreads.testsupport.Accounts.REGISTER_URL;
import static com.betterreads.testsupport.Accounts.USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers issuing, consuming, and resending email-verification tokens.
 *
 * <p>The mail-outbox worker is off ({@code mail.outbox.worker-enabled=false}) so enqueued rows
 * stay in the database and a test can read the plaintext token out of the payload without
 * racing a real send.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
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
class EmailVerificationIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String VERIFY_URL = "/api/v1/auth/verify-email";

    private static final String RESEND_URL = "/api/v1/auth/resend-verification";

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
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
        void shouldEnqueueVerificationMail() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(Accounts.registerPayload(objectMapper, USERNAME, EMAIL, PASSWORD)))
                .andExpect(status().isCreated());

            assertThat(mailOutboxRepository.findAll())
                .as("registration enqueues exactly one verification mail addressed to the user")
                .hasSize(1)
                .first()
                .satisfies(row -> assertThat(row.getRecipient()).isEqualTo(EMAIL))
                .satisfies(row -> assertThat(row.getTemplate())
                    .isEqualTo(MailOutboxService.TEMPLATE_EMAIL_VERIFICATION))
                .satisfies(row -> assertThat(AccountTestFixture.payloadField(objectMapper, row, FIELD_TOKEN))
                    .isNotBlank());

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
            final long userId = registerNewUser();
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(token)))
                .andExpect(status().isNoContent());

            assertThat(verifiedAt(userId))
                .as("verify must set email_verified_at on the user")
                .isNotNull();

            assertThat(emailTokenRepository.findActive(
                    userId, EmailToken.Purpose.EMAIL_VERIFICATION))
                .as("no unconsumed token remains after verify")
                .isEmpty();
        }

        @Test
        void replayingTheSameTokenStillReturns204AndLeavesUserVerifiedOnce() throws Exception {
            final long userId = registerNewUser();
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(token)))
                .andExpect(status().isNoContent());

            final Instant verifiedAtAfterFirst = verifiedAt(userId);
            assertThat(verifiedAtAfterFirst).isNotNull();

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(token)))
                .andExpect(status().isNoContent());

            assertThat(verifiedAt(userId))
                .as("replay must not move the verified timestamp")
                .isEqualTo(verifiedAtAfterFirst);
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            final long userId = registerNewUser();
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);
            AccountTestFixture.expireTokens(jdbcTemplate, userId, EmailToken.Purpose.EMAIL_VERIFICATION);

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(token)))
                .andExpect(status().isBadRequest());

            assertThat(verifiedAt(userId))
                .as("expired token must not flip the verified flag")
                .isNull();
        }

        @Test
        void rejectsUnknownToken() throws Exception {
            registerNewUser();

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(UNKNOWN_TOKEN)))
                .andExpect(status().isBadRequest());
        }

        /**
         * A resend before the original link is clicked marks the prior token consumed.
         * Presenting that superseded token has to fail: the user is unverified, and a 204 would
         * leave the frontend showing a verified state while {@code email_verified_at} stays
         * null. Only a token consumed by a real verification qualifies for the replay 204.
         */
        @Test
        void rejectsSupersededTokenWhenUserStillUnverified() throws Exception {
            final long userId = registerNewUser();
            final String firstToken = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);
            mailOutboxRepository.deleteAll();
            emailVerificationService.requestResend(EMAIL);

            mockMvc.perform(post(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload(firstToken)))
                .andExpect(status().isBadRequest());

            assertThat(verifiedAt(userId))
                .as("superseded token must leave the verified flag null")
                .isNull();
        }
    }

    @Nested
    @DisplayName("POST /auth/resend-verification")
    class ResendVerification {

        @Test
        void issuesNewTokenAndConsumesPriorOutstandingForUnverifiedUser() throws Exception {
            final long userId = registerNewUser();
            mailOutboxRepository.deleteAll();

            mockMvc.perform(post(RESEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AccountTestFixture.emailPayload(objectMapper, EMAIL)))
                .andExpect(status().isNoContent());

            assertThat(emailTokenRepository.findActive(
                    userId, EmailToken.Purpose.EMAIL_VERIFICATION))
                .as("resend leaves exactly one unconsumed token, the prior is consumed by the issue path")
                .hasSize(1);
            assertThat(mailOutboxRepository.findAll())
                .as("resend enqueues a fresh verification mail")
                .hasSize(1)
                .first()
                .satisfies(row -> assertThat(row.getRecipient()).isEqualTo(EMAIL));
        }

        @Test
        void returnsNoContentForUnknownEmailWithoutEnqueueing() throws Exception {
            mockMvc.perform(post(RESEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AccountTestFixture.emailPayload(objectMapper, UNKNOWN_EMAIL)))
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
            final long userId = registerNewUser();
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);
            emailVerificationService.verify(token);
            mailOutboxRepository.deleteAll();

            mockMvc.perform(post(RESEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AccountTestFixture.emailPayload(objectMapper, EMAIL)))
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
                    .content(AccountTestFixture.emailPayload(objectMapper, MIXED_CASE_EMAIL)))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll())
                .as("upper-case email matches the normalized stored value")
                .hasSize(1);
        }

        /**
         * Concurrent resends contend for the partial unique index on
         * {@code (user_id) WHERE consumed_at IS NULL}. The write lock on the user row serializes
         * them, so every call succeeds and one active token survives.
         */
        @Test
        void concurrentResendLeavesOneActiveToken() throws Exception {
            final long userId = registerNewUser();

            AccountTestFixture.runConcurrently(() -> emailVerificationService.requestResend(EMAIL));

            assertThat(emailTokenRepository.findActive(
                    userId, EmailToken.Purpose.EMAIL_VERIFICATION))
                .as("serialized resends leave exactly one active token, never zero or a duplicate")
                .hasSize(1);
        }
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long registerNewUser() throws Exception {
        return AccountTestFixture.registerUser(mockMvc, objectMapper, userRepository, USERNAME, EMAIL);
    }

    @Nullable
    private Instant verifiedAt(final long userId) {
        final User user = userRepository.findById(userId).orElseThrow();
        return user.getEmailVerifiedAt();
    }

    private String verifyPayload(final String token) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_TOKEN, token);
        return objectMapper.writeValueAsString(node);
    }
}
