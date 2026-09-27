package com.betterreads.features.account;

import com.betterreads.features.session.RefreshTokenRepository;
import com.betterreads.mailoutbox.MailOutboxRepository;
import com.betterreads.ratelimit.RateLimitFilter;
import com.betterreads.security.RefreshCookies;
import com.betterreads.testsupport.Accounts;
import com.betterreads.testsupport.ContainerizedTest;
import com.betterreads.users.UserRepository;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.testsupport.Accounts.EMAIL;
import static com.betterreads.testsupport.Accounts.PASSWORD;
import static com.betterreads.testsupport.Accounts.USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers self-service deletion through {@code DELETE /api/v1/auth/me} and the hard-delete sweep
 * that follows the grace period.
 *
 * <p>The grace period is 6 hours ({@code betterreads.auth.deletion.grace-period-hours}) so a test
 * can put a timestamp either side of the cutoff without waiting. {@code deleted_at} is written
 * through {@code JdbcTemplate} when a test needs a specific timestamp. The API path always
 * stamps {@code now()}.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.refresh-cookie.secure=true",
    "auth.rate-limit.forgot-password-capacity=1000",
    "auth.rate-limit.forgot-password-refill-tokens=1000",
    "auth.rate-limit.forgot-password-refill-seconds=1",
    "betterreads.auth.deletion.grace-period-hours=6",
    "betterreads.auth.deletion.scheduler-enabled=false",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false"
})
class AccountDeletionIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String OTHER_USERNAME = "mustang";

    private static final String OTHER_EMAIL = "mustang@example.com";

    private static final long IN_GRACE_HOURS_AGO = 1L;

    private static final long PAST_GRACE_HOURS_AGO = 7L;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailTokenRepository emailTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private AccountDeletionSweep accountDeletionSweep;

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
        jdbcTemplate.update("DELETE FROM mail_outbox");
        jdbcTemplate.update("DELETE FROM email_token");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM app_user");
        rateLimitFilter.reset();
    }

    @Nested
    @DisplayName("DELETE /auth/me")
    class DeleteMe {

        @Test
        void softDeletesUserAndKillsAuthSession() throws Exception {
            final long userId = registerAndSeedOutstandingTokens();
            final Tokens tokens = loginAndCapture(USERNAME);

            mockMvc.perform(delete(Accounts.ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());

            assertThat(deletedAt(userId))
                .as("soft-delete writes a non-null timestamp, the row stays in app_user during the grace window")
                .isNotNull();
            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId))
                .as("delete revokes every refresh token so other devices are kicked off")
                .isZero();
            assertThat(activeEmailTokenCount(userId))
                .as("outstanding password-reset and verification tokens are invalidated by the delete")
                .isZero();
        }

        @Test
        void repeatDeleteIsIdempotent() throws Exception {
            registerAndSeedOutstandingTokens();
            final Tokens tokens = loginAndCapture(USERNAME);

            mockMvc.perform(delete(Accounts.ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());

            mockMvc.perform(delete(Accounts.ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());
        }

        @Test
        void unauthenticatedDeleteReturns401() throws Exception {
            mockMvc.perform(delete(Accounts.ME_URL))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Side effects after delete")
    class SideEffects {

        @Test
        void forgotPasswordIsSilentForDeletedAccount() throws Exception {
            registerAndSeedOutstandingTokens();
            final Tokens tokens = loginAndCapture(USERNAME);
            mockMvc.perform(delete(Accounts.ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());
            mailOutboxRepository.deleteAll();

            mockMvc.perform(post(AccountTestFixture.FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AccountTestFixture.emailPayload(objectMapper, EMAIL)))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll())
                .as("forgot-password must not enqueue mail for a deleted account, "
                    + "since that would leak that the address was once registered")
                .isEmpty();
        }

        @Test
        void reRegistrationDuringGraceIsBlocked() throws Exception {
            registerAndSeedOutstandingTokens();
            final Tokens tokens = loginAndCapture(USERNAME);
            mockMvc.perform(delete(Accounts.ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());

            mockMvc.perform(post(Accounts.REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(Accounts.registerPayload(objectMapper, USERNAME, EMAIL, PASSWORD)))
                .andExpect(status().isConflict());

            assertThat(rawRowCountForEmail(EMAIL))
                .as("the soft-deleted row still occupies the email slot, no second row was inserted")
                .isOne();
        }
    }

    @Nested
    @DisplayName("Other users")
    class Isolation {

        @Test
        void deleteDoesNotTouchOtherUsersAuthMaterial() throws Exception {
            registerUser(USERNAME, EMAIL);
            final long mustangId = registerUser(OTHER_USERNAME, OTHER_EMAIL);
            passwordResetService.requestReset(OTHER_EMAIL);
            final Tokens darrowTokens = loginAndCapture(USERNAME);
            final Tokens mustangTokens = loginAndCapture(OTHER_USERNAME);

            mockMvc.perform(delete(Accounts.ME_URL).header(AUTH_HEADER, BEARER_PREFIX + darrowTokens.accessToken()))
                .andExpect(status().isNoContent());

            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, mustangId))
                .as("mustang's refresh tokens are untouched when darrow deletes their account")
                .isPositive();
            assertThat(activeEmailTokenCount(mustangId))
                .as("mustang's outstanding password-reset token is untouched")
                .isPositive();
            mockMvc.perform(post(Accounts.REFRESH_URL)
                    .cookie(new Cookie(RefreshCookies.COOKIE_NAME, mustangTokens.refreshCookieValue())))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Hard-delete sweep")
    class Sweep {

        @Test
        void sweepDeletesOnlyUsersPastGrace() throws Exception {
            final long inGraceUserId = registerUser(USERNAME, EMAIL);
            final long pastGraceUserId = registerUser(OTHER_USERNAME, OTHER_EMAIL);
            setDeletedAt(inGraceUserId, Instant.now().minus(IN_GRACE_HOURS_AGO, ChronoUnit.HOURS));
            setDeletedAt(pastGraceUserId, Instant.now().minus(PAST_GRACE_HOURS_AGO, ChronoUnit.HOURS));

            final int swept = accountDeletionSweep.sweep();

            assertThat(swept)
                .as("only the user past the 6-hour grace window is hard-deleted")
                .isOne();
            assertThat(rawRowCountForUser(pastGraceUserId))
                .as("past-grace row removed from app_user")
                .isZero();
            assertThat(rawRowCountForUser(inGraceUserId))
                .as("in-grace row still in app_user")
                .isOne();
        }

        @Test
        void sweepHardDeletesAndCascadesDependentRows() throws Exception {
            final long userId = registerAndSeedOutstandingTokens();
            loginAndCapture(USERNAME);
            assertThat(refreshTokenCount(userId))
                .as("seeded refresh token before sweep")
                .isPositive();
            assertThat(activeEmailTokenCount(userId))
                .as("seeded email tokens before sweep")
                .isPositive();
            setDeletedAt(userId, Instant.now().minus(PAST_GRACE_HOURS_AGO, ChronoUnit.HOURS));

            accountDeletionSweep.sweep();

            assertThat(rawRowCountForUser(userId))
                .as("hard-delete removed the app_user row")
                .isZero();
            assertThat(refreshTokenCount(userId))
                .as("refresh_token rows cascade-deleted via ON DELETE CASCADE")
                .isZero();
            assertThat(rawEmailTokenCountForUser(userId))
                .as("email_token rows cascade-deleted via ON DELETE CASCADE")
                .isZero();
        }
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long registerAndSeedOutstandingTokens() throws Exception {
        final long userId = registerUser(USERNAME, EMAIL);
        passwordResetService.requestReset(EMAIL);
        emailVerificationService.requestResend(EMAIL);
        return userId;
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long registerUser(final String username, final String email) throws Exception {
        return AccountTestFixture.registerUser(mockMvc, objectMapper, userRepository, username, email);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private Tokens loginAndCapture(final String identifier) throws Exception {
        final MvcResult result =
            AccountTestFixture.login(mockMvc, objectMapper, identifier, PASSWORD);
        final String body = result.getResponse().getContentAsString();
        final JsonNode root = objectMapper.readTree(body);
        final String accessToken = root.at("/data/accessToken").asString();
        return new Tokens(accessToken, Accounts.refreshCookie(result));
    }

    private long refreshTokenCount(final long userId) {
        return refreshTokenRepository.findAll().stream()
            .filter(rt -> rt.getUserId() == userId)
            .count();
    }

    private long activeEmailTokenCount(final long userId) {
        return Arrays.stream(EmailToken.Purpose.values())
            .mapToLong(purpose -> emailTokenRepository.findActive(userId, purpose).size())
            .sum();
    }

    private void setDeletedAt(final long userId, final Instant when) {
        jdbcTemplate.update("UPDATE app_user SET deleted_at = ? WHERE user_id = ?",
            when.atOffset(ZoneOffset.UTC), userId);
    }

    private Instant deletedAt(final long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT deleted_at FROM app_user WHERE user_id = ?",
            (rs, rowNum) -> {
                final OffsetDateTime value = rs.getObject(1, OffsetDateTime.class);
                return value == null ? null : value.toInstant();
            },
            userId);
    }

    private int rawRowCountForUser(final long userId) {
        return orZero(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE user_id = ?", Integer.class, userId));
    }

    private int rawRowCountForEmail(final String email) {
        return orZero(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email));
    }

    private int rawEmailTokenCountForUser(final long userId) {
        return orZero(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_token WHERE user_id = ?", Integer.class, userId));
    }

    private static int orZero(@Nullable final Integer count) {
        return count == null ? 0 : count;
    }

    private record Tokens(String accessToken, String refreshCookieValue) { }
}
