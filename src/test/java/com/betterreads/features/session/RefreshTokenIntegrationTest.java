package com.betterreads.features.session;

import com.betterreads.mailoutbox.MailOutboxRepository;
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

import jakarta.servlet.http.Cookie;

import java.time.Instant;

import org.hamcrest.Matchers;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.testsupport.Accounts.EMAIL;
import static com.betterreads.testsupport.Accounts.LOGIN_URL;
import static com.betterreads.testsupport.Accounts.PASSWORD;
import static com.betterreads.testsupport.Accounts.REFRESH_URL;
import static com.betterreads.testsupport.Accounts.REGISTER_URL;
import static com.betterreads.testsupport.Accounts.USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.refresh-cookie.secure=true",
    "mail.outbox.worker-enabled=false"
})
class RefreshTokenIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String LOGOUT_URL = "/api/v1/auth/logout";

    private static final String COOKIE_NAME = "br_refresh";

    private static final String COOKIE_PATH = "/api/v1/auth";

    private static final String SET_COOKIE_HEADER = "Set-Cookie";

    private static final long REFRESH_TTL_SECONDS = 30L * 24 * 60 * 60;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        mailOutboxRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM app_user");
        rateLimitFilter.reset();
    }

    @Nested
    @DisplayName("Cookie contract on register")
    class CookieContract {

        @Test
        void carriesTheConfiguredCookieAttributes() throws Exception {
            final MvcResult result = mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(Accounts.registerPayload(objectMapper, USERNAME, EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().httpOnly(COOKIE_NAME, true))
                .andExpect(cookie().secure(COOKIE_NAME, true))
                .andExpect(cookie().path(COOKIE_NAME, COOKIE_PATH))
                .andExpect(cookie().maxAge(COOKIE_NAME, (int) REFRESH_TTL_SECONDS))
                .andReturn();

            assertThat(result.getResponse().getHeader(SET_COOKIE_HEADER))
                .as("the configured SameSite (Strict by default) is written to the raw Set-Cookie header")
                .containsIgnoringCase("samesite=strict");
        }

        @Test
        void omitsRefreshTokenFromJsonBody() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(Accounts.registerPayload(objectMapper, USERNAME, EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh")
    class Refresh {

        @Test
        void rotatesAndRevokesOldToken() throws Exception {
            final long userId = seedUser();
            final String original = loginAndExtractCookieValue();

            refresh(original)
                .andExpect(status().isOk())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().value(COOKIE_NAME, Matchers.not(Matchers.equalTo(original))));

            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId))
                .as("rotation leaves exactly one active token for this user")
                .isEqualTo(1L);
        }

        @Test
        void replayingRevokedTokenRevokesEntireChain() throws Exception {
            final long userId = seedUser();
            final String original = loginAndExtractCookieValue();

            refresh(original)
                .andExpect(status().isOk());

            refresh(original)
                .andExpect(status().isUnauthorized());

            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isZero();
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            final long userId = seedUser();
            final String original = loginAndExtractCookieValue();
            expireUserTokens(userId);

            refresh(original)
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsTokenForDeletedUser() throws Exception {
            final long userId = seedUser();
            final String original = loginAndExtractCookieValue();
            final User user = userRepository.findById(userId).orElseThrow();
            user.setDeletedAt(Instant.now());
            userRepository.save(user);

            refresh(original)
                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldRejectUnknownToken() throws Exception {
            refresh("not-a-real-token")
                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldRejectLoggedOutTokenWithoutRevokingOtherSessions() throws Exception {
            final long userId = seedUser();
            final String loggedOut = loginAndExtractCookieValue();
            loginAndExtractCookieValue();
            logout(loggedOut)
                .andExpect(status().isNoContent());

            refresh(loggedOut)
                .andExpect(status().isUnauthorized());

            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isEqualTo(1L);
        }

        @Test
        void rejectsRequestWithoutCookie() throws Exception {
            mockMvc.perform(post(REFRESH_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldRejectAFormPostWithoutRotatingTheToken() throws Exception {
            seedUser();
            final String original = loginAndExtractCookieValue();

            final ResultActions formPost = mockMvc.perform(post(REFRESH_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(new Cookie(COOKIE_NAME, original)));

            formPost.andExpect(status().isUnsupportedMediaType());
            refresh(original)
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /auth/logout")
    class Logout {

        @Test
        void revokesTokenAndClearsCookie() throws Exception {
            final long userId = seedUser();
            final String original = loginAndExtractCookieValue();

            logout(original)
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().maxAge(COOKIE_NAME, 0))
                .andExpect(cookie().path(COOKIE_NAME, COOKIE_PATH));

            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isZero();
        }

        @Test
        void isIdempotentWithoutCookie() throws Exception {
            mockMvc.perform(post(LOGOUT_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        }

        @Test
        void shouldRejectAFormPostWithoutRevokingTheToken() throws Exception {
            final long userId = seedUser();
            final String original = loginAndExtractCookieValue();

            final ResultActions formPost = mockMvc.perform(post(LOGOUT_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(new Cookie(COOKIE_NAME, original)));

            formPost.andExpect(status().isUnsupportedMediaType());
            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isEqualTo(1L);
        }
    }

    private long seedUser() {
        return Accounts.seedUser(userRepository, passwordEncoder, USERNAME, EMAIL, PASSWORD);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions refresh(final String cookieValue) throws Exception {
        return jsonPost(REFRESH_URL, cookieValue);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions logout(final String cookieValue) throws Exception {
        return jsonPost(LOGOUT_URL, cookieValue);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions jsonPost(final String url, final String cookieValue) throws Exception {
        return mockMvc.perform(post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .cookie(new Cookie(COOKIE_NAME, cookieValue)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private String loginAndExtractCookieValue() throws Exception {
        final MvcResult result = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(Accounts.loginPayload(objectMapper, USERNAME, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists(COOKIE_NAME))
            .andReturn();
        return Accounts.refreshCookie(result);
    }

    private void expireUserTokens(final long userId) {
        refreshTokenRepository.findAll().stream()
            .filter(rt -> rt.getUserId() == userId)
            .forEach(rt -> {
                final Instant past = Accounts.expiredIssuedAt();
                rt.setIssuedAt(past);
                rt.setExpiresAt(past.plusSeconds(1));
                refreshTokenRepository.save(rt);
            });
    }
}
