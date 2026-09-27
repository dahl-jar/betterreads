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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.features.account.AccountTestFixture.FIELD_TOKEN;
import static com.betterreads.features.account.AccountTestFixture.FORGOT_URL;
import static com.betterreads.features.account.AccountTestFixture.UNKNOWN_EMAIL;
import static com.betterreads.features.account.AccountTestFixture.UNKNOWN_TOKEN;
import static com.betterreads.testsupport.Accounts.EMAIL;
import static com.betterreads.testsupport.Accounts.MIXED_CASE_EMAIL;
import static com.betterreads.testsupport.Accounts.USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers issuing a reset token, consuming it once, and the refresh-token revoke that follows a
 * successful reset.
 *
 * <p>The mail-outbox worker is off ({@code mail.outbox.worker-enabled=false}) so enqueued rows
 * stay in the database and a test can read the plaintext token out of the payload without
 * racing a real send.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.refresh-cookie.secure=true",
    "auth.rate-limit.forgot-password-capacity=1000",
    "auth.rate-limit.forgot-password-refill-tokens=1000",
    "auth.rate-limit.forgot-password-refill-seconds=1",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false"
})
class PasswordResetIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String RESET_URL = "/api/v1/auth/reset-password";

    private static final String OLD_PASSWORD = "OldP4ssword!";

    private static final String NEW_PASSWORD = "BrandN3wPass!";

    private static final String MULTIBYTE_PASSWORD = "é".repeat(40);

    private static final String FIELD_NEW_PASSWORD = "newPassword";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailTokenRepository emailTokenRepository;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private EmailVerificationService emailVerificationService;

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
    @DisplayName("POST /auth/forgot-password")
    class ForgotPassword {

        @Test
        void enqueuesOutboxRowForKnownEmail() throws Exception {
            seedUser();

            mockMvc.perform(post(FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AccountTestFixture.emailPayload(objectMapper, EMAIL)))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll())
                .as("exactly one outbox row enqueued for the matching account, addressed to the original email")
                .hasSize(1)
                .first()
                .satisfies(row -> assertThat(row.getRecipient()).isEqualTo(EMAIL))
                .satisfies(row -> assertThat(row.getTemplate()).isEqualTo(MailOutboxService.TEMPLATE_PASSWORD_RESET))
                .satisfies(row -> assertThat(AccountTestFixture.payloadField(objectMapper, row, FIELD_TOKEN))
                    .isNotBlank());
        }

        @Test
        void returnsNoContentForUnknownEmailWithoutEnqueueing() throws Exception {
            mockMvc.perform(post(FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AccountTestFixture.emailPayload(objectMapper, UNKNOWN_EMAIL)))
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
                    .content(AccountTestFixture.emailPayload(objectMapper, MIXED_CASE_EMAIL)))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll()).hasSize(1);
        }

        @Test
        void rejectsMalformedEmail() throws Exception {
            mockMvc.perform(post(FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AccountTestFixture.emailPayload(objectMapper, "not-an-email")))
                .andExpect(status().isBadRequest());

            assertThat(mailOutboxRepository.findAll()).isEmpty();
        }

        /**
         * Concurrent reset requests contend for the partial unique index on
         * {@code (user_id) WHERE consumed_at IS NULL}. The write lock on the user row serializes
         * them, so every call succeeds and one active token survives.
         *
         * <p>The service is called directly so the per-IP rate limit filter does not cap the
         * thread count.
         */
        @Test
        void concurrentRequestsLeaveOneActiveToken() throws Exception {
            final long userId = seedUser();

            AccountTestFixture.runConcurrently(() -> passwordResetService.requestReset(EMAIL));

            assertThat(emailTokenRepository.findActive(userId, EmailToken.Purpose.PASSWORD_RESET))
                .as("serialized requests leave exactly one active token, never zero or a duplicate")
                .hasSize(1);
        }
    }

    @Nested
    @DisplayName("POST /auth/reset-password")
    class ResetPassword {

        @Test
        void resetsPasswordAndRevokesAllRefreshTokens() throws Exception {
            final long userId = seedUser();
            AccountTestFixture.login(mockMvc, objectMapper, USERNAME, OLD_PASSWORD);
            requestForgotPassword();
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload(token, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId))
                .as("successful reset revokes every refresh token so other devices are kicked off")
                .isZero();
            assertThat(storedPasswordMatches(userId, NEW_PASSWORD))
                .as("the stored hash is the new password")
                .isTrue();
        }

        @Test
        void rejectsAlreadyConsumedToken() throws Exception {
            seedUser();
            requestForgotPassword();
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);

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
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);
            AccountTestFixture.expireTokens(jdbcTemplate, userId, EmailToken.Purpose.PASSWORD_RESET);

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
                    .content(resetPayload(UNKNOWN_TOKEN, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectVerificationToken() throws Exception {
            final long userId = seedUser();
            emailVerificationService.requestResend(EMAIL);
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload(token, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());

            assertThat(storedPasswordMatches(userId, OLD_PASSWORD))
                .as("a verification token must not change the password")
                .isTrue();
        }

        @Test
        void rejectsWeakPassword() throws Exception {
            seedUser();
            requestForgotPassword();
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload(token, "abc")))
                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectPasswordOverSeventyTwoBytes() throws Exception {
            final long userId = seedUser();
            requestForgotPassword();
            final String token = AccountTestFixture.readEnqueuedToken(mailOutboxRepository, objectMapper);

            mockMvc.perform(post(RESET_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resetPayload(token, MULTIBYTE_PASSWORD)))
                .andExpect(status().isBadRequest());

            assertThat(storedPasswordMatches(userId, OLD_PASSWORD))
                .as("an over-long password must leave the old one in place")
                .isTrue();
        }
    }

    private long seedUser() {
        return Accounts.seedUser(userRepository, passwordEncoder, USERNAME, EMAIL, OLD_PASSWORD);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void requestForgotPassword() throws Exception {
        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(AccountTestFixture.emailPayload(objectMapper, EMAIL)))
            .andExpect(status().isNoContent());
    }

    private boolean storedPasswordMatches(final long userId, final String rawPassword) {
        final User user = userRepository.findById(userId).orElseThrow();
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    private String resetPayload(final String token, final String newPassword) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_TOKEN, token);
        node.put(FIELD_NEW_PASSWORD, newPassword);
        return objectMapper.writeValueAsString(node);
    }
}
