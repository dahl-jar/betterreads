package com.betterreads.auth;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.auth.refresh.RefreshTokenRepository;
import com.betterreads.auth.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.Cookie;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.hamcrest.Matchers;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the refresh-token flow end-to-end: rotation, replay defense, expiry, deleted user, and
 * the cookie contract that carries the token between client and server. The token never appears
 * in the JSON body; it lives only in the {@code br_refresh} {@code HttpOnly} cookie.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=120",
    "jwt.refresh-expiration-days=30",
    "auth.refresh-cookie.secure=true"
})
@SuppressWarnings("PMD.TooManyStaticImports")
class RefreshTokenIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final String REFRESH_URL = "/api/v1/auth/refresh";

    private static final String LOGOUT_URL = "/api/v1/auth/logout";

    private static final String REGISTER_URL = "/api/v1/auth/register";

    private static final String COOKIE_NAME = "br_refresh";

    private static final String COOKIE_PATH = "/api/v1/auth";

    private static final String SET_COOKIE_HEADER = "Set-Cookie";

    private static final String USERNAME = "alice";

    private static final String EMAIL = "alice@example.com";

    private static final String PASSWORD = "Sup3rSecret!";

    private static final String FIELD_USERNAME = "username";

    private static final String FIELD_EMAIL = "email";

    private static final String FIELD_PASSWORD = "password";

    private static final String FIELD_IDENTIFIER = "identifier";

    private static final long REFRESH_TTL_SECONDS = 30L * 24 * 60 * 60;

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
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        rateLimitFilter.reset();
    }

    @Nested
    @DisplayName("Cookie contract on register")
    class CookieContract {

        @Test
        void setsHttpOnlySecureSameSiteStrictCookieScopedToAuthPath() throws Exception {
            final MvcResult result = mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerPayload(USERNAME, EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().httpOnly(COOKIE_NAME, true))
                .andExpect(cookie().secure(COOKIE_NAME, true))
                .andExpect(cookie().path(COOKIE_NAME, COOKIE_PATH))
                .andExpect(cookie().maxAge(COOKIE_NAME, (int) REFRESH_TTL_SECONDS))
                .andReturn();

            assertThat(result.getResponse().getHeader(SET_COOKIE_HEADER))
                .as("SameSite attribute must be Strict on the raw Set-Cookie header")
                .containsIgnoringCase("samesite=strict");
        }

        @Test
        void omitsRefreshTokenFromJsonBody() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerPayload(USERNAME, EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh")
    class Refresh {

        @Test
        void rotatesAndRevokesOldToken() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String original = loginAndExtractCookieValue();

            mockMvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE_NAME, original)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().value(COOKIE_NAME, Matchers.not(Matchers.equalTo(original))));

            assertThat(refreshTokenRepository.findAll())
                .as("rotation leaves exactly one active token for this user")
                .allSatisfy(row -> assertThat(row.getUserId()).isEqualTo(userId))
                .satisfies(rows -> assertThat(activeTokenCountForUser(userId)).isEqualTo(1L));
        }

        @Test
        void replayingRevokedTokenRevokesEntireChain() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String original = loginAndExtractCookieValue();

            mockMvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE_NAME, original)))
                .andExpect(status().isOk());

            mockMvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE_NAME, original)))
                .andExpect(status().isUnauthorized());

            assertThat(activeTokenCountForUser(userId)).isZero();
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String original = loginAndExtractCookieValue();
            expireUserTokens(userId);

            mockMvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE_NAME, original)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsTokenForDeletedUser() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String original = loginAndExtractCookieValue();
            userRepository.deleteById(userId);

            mockMvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE_NAME, original)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsRequestWithoutCookie() throws Exception {
            seedUser(USERNAME, EMAIL, PASSWORD);

            mockMvc.perform(post(REFRESH_URL))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /auth/logout")
    class Logout {

        @Test
        void revokesTokenAndClearsCookie() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String original = loginAndExtractCookieValue();

            mockMvc.perform(post(LOGOUT_URL).cookie(new Cookie(COOKIE_NAME, original)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().maxAge(COOKIE_NAME, 0))
                .andExpect(cookie().path(COOKIE_NAME, COOKIE_PATH));

            assertThat(activeTokenCountForUser(userId)).isZero();

            mockMvc.perform(post(REFRESH_URL).cookie(new Cookie(COOKIE_NAME, original)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void isIdempotentWithoutCookie() throws Exception {
            mockMvc.perform(post(LOGOUT_URL))
                .andExpect(status().isNoContent());
        }
    }

    private long seedUser(final String username, final String email, final String rawPassword) {
        final User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user).getUserId();
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private String loginAndExtractCookieValue() throws Exception {
        final String setCookie = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(USERNAME, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists(COOKIE_NAME))
            .andReturn()
            .getResponse()
            .getHeader(SET_COOKIE_HEADER);
        if (setCookie == null) {
            throw new IllegalStateException("login response is missing the Set-Cookie header");
        }
        return parseCookieValue(setCookie);
    }

    private String parseCookieValue(final String setCookieHeader) {
        final int equals = setCookieHeader.indexOf('=');
        final int semi = setCookieHeader.indexOf(';');
        return setCookieHeader.substring(equals + 1, semi);
    }

    private long activeTokenCountForUser(final long userId) {
        return refreshTokenRepository.findAll().stream()
            .filter(rt -> rt.getUserId() == userId)
            .filter(rt -> rt.getRevokedAt() == null)
            .count();
    }

    private void expireUserTokens(final long userId) {
        refreshTokenRepository.findAll().stream()
            .filter(rt -> rt.getUserId() == userId)
            .forEach(rt -> {
                final Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
                rt.setIssuedAt(past);
                rt.setExpiresAt(past.plusSeconds(1));
                refreshTokenRepository.save(rt);
            });
    }

    private String registerPayload(final String username, final String email, final String password)
        throws JsonProcessingException {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_USERNAME, username);
        node.put(FIELD_EMAIL, email);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }

    private String loginPayload(final String identifier, final String password)
        throws JsonProcessingException {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_IDENTIFIER, identifier);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }
}
