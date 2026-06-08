package com.betterreads.auth;

import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.betterreads.auth.deletion.AccountDeletionSweep;
import com.betterreads.auth.emailverification.EmailVerificationService;
import com.betterreads.auth.passwordreset.PasswordResetService;
import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.auth.refresh.RefreshTokenRepository;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.auth.token.EmailToken;
import com.betterreads.auth.token.EmailTokenRepository;
import com.betterreads.mail.outbox.MailOutboxRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.Cookie;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the account-deletion flow end-to-end: self-service soft-delete via
 * {@code DELETE /api/v1/auth/me}, the side effects on refresh and email tokens, the
 * grace-window-block on re-registration, and the scheduled hard-delete sweep.
 *
 * <p>Grace period is overridden to 6 hours via {@code betterreads.auth.deletion.grace-period-hours}
 * so tests can shape timelines around a known cutoff without waiting real wall-clock time.
 * The {@code deleted_at} column is written directly via {@link JdbcTemplate} where the test
 * needs a soft-delete with a specific timestamp (the API path always uses {@code now()}).
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
    "betterreads.auth.deletion.grace-period-hours=6",
    "betterreads.auth.deletion.scheduler-enabled=false",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false"
})
@SuppressWarnings({"PMD.TooManyMethods", "PMD.ExcessiveImports"})
class AccountDeletionIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String REGISTER_URL = "/api/v1/auth/register";

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final String REFRESH_URL = "/api/v1/auth/refresh";

    private static final String FORGOT_URL = "/api/v1/auth/forgot-password";

    private static final String ME_URL = "/api/v1/auth/me";

    private static final String COOKIE_NAME = "br_refresh";

    private static final String SET_COOKIE_HEADER = "Set-Cookie";

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String USERNAME = "alice";

    private static final String EMAIL = "alice@example.com";

    private static final String PASSWORD = "Sup3rSecret!";

    private static final String OTHER_USERNAME = "bob";

    private static final String OTHER_EMAIL = "bob@example.com";

    private static final String FIELD_USERNAME = "username";

    private static final String FIELD_EMAIL = "email";

    private static final String FIELD_PASSWORD = "password";

    private static final String FIELD_IDENTIFIER = "identifier";

    private static final long IN_GRACE_HOURS_AGO = 1L;

    private static final long PAST_GRACE_HOURS_AGO = 7L;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

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
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .addFilter(springSecurityFilterChain)
            .build();
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
            final Tokens tokens = loginAndCapture(USERNAME, PASSWORD);

            mockMvc.perform(delete(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());

            assertThat(deletedAt(userId))
                .as("soft-delete writes a non-null timestamp; the row stays in app_user during the grace window")
                .isNotNull();
            assertThat(activeRefreshTokenCount(userId))
                .as("delete revokes every refresh token so other devices are kicked off")
                .isZero();
            assertThat(activeEmailTokenCount(userId))
                .as("outstanding password-reset and verification tokens are invalidated by the delete")
                .isZero();

            mockMvc.perform(post(LOGIN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload(USERNAME, PASSWORD)))
                .andExpect(status().isUnauthorized());

            mockMvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE_NAME, tokens.refreshCookieValue())))
                .andExpect(status().isUnauthorized());

            mockMvc.perform(get(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void repeatDeleteIsIdempotent() throws Exception {
            registerAndSeedOutstandingTokens();
            final Tokens tokens = loginAndCapture(USERNAME, PASSWORD);

            mockMvc.perform(delete(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());

            mockMvc.perform(delete(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());
        }

        @Test
        void unauthenticatedDeleteReturns401() throws Exception {
            mockMvc.perform(delete(ME_URL))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Side effects after delete")
    class SideEffects {

        @Test
        void forgotPasswordIsSilentForDeletedAccount() throws Exception {
            registerAndSeedOutstandingTokens();
            final Tokens tokens = loginAndCapture(USERNAME, PASSWORD);
            mockMvc.perform(delete(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());
            mailOutboxRepository.deleteAll();

            mockMvc.perform(post(FORGOT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(forgotPayload(EMAIL)))
                .andExpect(status().isNoContent());

            assertThat(mailOutboxRepository.findAll())
                .as("forgot-password must not enqueue mail for a deleted account, "
                    + "since that would leak that the address was once registered")
                .isEmpty();
        }

        @Test
        void reRegistrationDuringGraceIsBlocked() throws Exception {
            registerAndSeedOutstandingTokens();
            final Tokens tokens = loginAndCapture(USERNAME, PASSWORD);
            mockMvc.perform(delete(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + tokens.accessToken()))
                .andExpect(status().isNoContent());

            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerPayload(USERNAME, EMAIL, PASSWORD)))
                .andExpect(status().is4xxClientError());

            assertThat(rawRowCountForEmail(EMAIL))
                .as("the soft-deleted row still occupies the email slot; no second row was inserted")
                .isOne();
        }
    }

    @Nested
    @DisplayName("Blast-radius isolation")
    class Isolation {

        @Test
        void deleteDoesNotTouchOtherUsersAuthMaterial() throws Exception {
            registerUser(USERNAME, EMAIL, PASSWORD);
            final long bobId = registerUser(OTHER_USERNAME, OTHER_EMAIL, PASSWORD);
            passwordResetService.requestReset(OTHER_EMAIL);
            final Tokens aliceTokens = loginAndCapture(USERNAME, PASSWORD);
            final Tokens bobTokens = loginAndCapture(OTHER_USERNAME, PASSWORD);

            mockMvc.perform(delete(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + aliceTokens.accessToken()))
                .andExpect(status().isNoContent());

            assertThat(activeRefreshTokenCount(bobId))
                .as("bob's refresh tokens are untouched when alice deletes her account")
                .isPositive();
            assertThat(activeEmailTokenCount(bobId))
                .as("bob's outstanding password-reset token is untouched")
                .isPositive();
            mockMvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE_NAME, bobTokens.refreshCookieValue())))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Hard-delete sweep")
    class Sweep {

        @Test
        void sweepDeletesOnlyUsersPastGrace() throws Exception {
            final long inGraceUserId = registerUser(USERNAME, EMAIL, PASSWORD);
            final long pastGraceUserId = registerUser(OTHER_USERNAME, OTHER_EMAIL, PASSWORD);
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
            loginAndCapture(USERNAME, PASSWORD);
            assertThat(refreshTokenRepository.findAllByUserId(userId))
                .as("seeded refresh token before sweep")
                .isNotEmpty();
            assertThat(activeEmailTokenCount(userId))
                .as("seeded email tokens before sweep")
                .isPositive();
            setDeletedAt(userId, Instant.now().minus(PAST_GRACE_HOURS_AGO, ChronoUnit.HOURS));

            accountDeletionSweep.sweep();

            assertThat(rawRowCountForUser(userId))
                .as("hard-delete removed the app_user row")
                .isZero();
            assertThat(refreshTokenRepository.findAllByUserId(userId))
                .as("refresh_token rows cascade-deleted via ON DELETE CASCADE")
                .isEmpty();
            assertThat(rawEmailTokenCountForUser(userId))
                .as("email_token rows cascade-deleted via ON DELETE CASCADE")
                .isZero();
        }

        @Test
        void reRegistrationSucceedsAfterSweep() throws Exception {
            final long userId = registerUser(USERNAME, EMAIL, PASSWORD);
            setDeletedAt(userId, Instant.now().minus(PAST_GRACE_HOURS_AGO, ChronoUnit.HOURS));
            accountDeletionSweep.sweep();

            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerPayload(USERNAME, EMAIL, PASSWORD)))
                .andExpect(status().isCreated());

            assertThat(rawRowCountForEmail(EMAIL))
                .as("a fresh row owns the email slot after the sweep released it")
                .isOne();
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long registerAndSeedOutstandingTokens() throws Exception {
        final long userId = registerUser(USERNAME, EMAIL, PASSWORD);
        passwordResetService.requestReset(EMAIL);
        emailVerificationService.requestResend(EMAIL);
        return userId;
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private long registerUser(final String username, final String email, final String password)
        throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(username, email, password)))
            .andExpect(status().isCreated());
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("registration succeeded but user not found"))
            .getUserId();
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private Tokens loginAndCapture(final String identifier, final String password) throws Exception {
        final MvcResult result = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(identifier, password)))
            .andExpect(status().isOk())
            .andReturn();
        final String body = result.getResponse().getContentAsString();
        final String accessToken = objectMapper.readTree(body).at("/data/accessToken").asString();
        final String setCookie = result.getResponse().getHeader(SET_COOKIE_HEADER);
        if (setCookie == null) {
            throw new IllegalStateException("login response is missing the Set-Cookie header");
        }
        final int equals = setCookie.indexOf('=');
        final int semi = setCookie.indexOf(';');
        return new Tokens(accessToken, setCookie.substring(equals + 1, semi));
    }

    private long activeRefreshTokenCount(final long userId) {
        return refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId).size();
    }

    private long activeEmailTokenCount(final long userId) {
        long total = 0;
        for (final EmailToken.Purpose purpose : EmailToken.Purpose.values()) {
            total += emailTokenRepository.findActive(userId, purpose).size();
        }
        return total;
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
        final Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int rawRowCountForEmail(final String email) {
        final Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email);
        return count == null ? 0 : count;
    }

    private int rawEmailTokenCountForUser(final long userId) {
        final Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_token WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private String registerPayload(final String username, final String email, final String password) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_USERNAME, username);
        node.put(FIELD_EMAIL, email);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }

    private String loginPayload(final String identifier, final String password) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_IDENTIFIER, identifier);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }

    private String forgotPayload(final String email) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_EMAIL, email);
        return objectMapper.writeValueAsString(node);
    }

    /** Pair of an access JWT and the {@code br_refresh} cookie value captured from a login. */
    private record Tokens(String accessToken, String refreshCookieValue) { }
}
