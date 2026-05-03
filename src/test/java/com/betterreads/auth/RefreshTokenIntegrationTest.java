package com.betterreads.auth;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.auth.refresh.RefreshToken;
import com.betterreads.auth.refresh.RefreshTokenRepository;
import com.betterreads.auth.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the refresh-token flow against a real Postgres via Testcontainers. Covers the happy
 * path (rotation issues a new pair, old token is revoked), the security-critical replay defence
 * (presenting a revoked-and-replaced token wipes the entire user's chain), expiry, and the
 * deleted-user case.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=120",
    "jwt.refresh-expiration-days=30"
})
class RefreshTokenIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final String REFRESH_URL = "/api/v1/auth/refresh";

    private static final String LOGOUT_URL = "/api/v1/auth/logout";

    private static final String USERNAME = "alice";

    private static final String EMAIL = "alice@example.com";

    private static final String PASSWORD = "Sup3rSecret!";

    private static final String FIELD_REFRESH_TOKEN = "refreshToken";

    private static final String FIELD_PASSWORD = "password";

    private static final String FIELD_IDENTIFIER = "identifier";

    private static final String JSON_ACCESS_TOKEN = "$.accessToken";

    private static final String JSON_REFRESH_TOKEN = "$.refreshToken";

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
    @DisplayName("POST /auth/login response shape")
    class LoginShape {

        @Test
        void includesBothAccessAndRefreshTokens() throws Exception {
            seedUser(USERNAME, EMAIL, PASSWORD);

            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ACCESS_TOKEN).isNotEmpty())
                .andExpect(jsonPath(JSON_REFRESH_TOKEN).isNotEmpty());

            assertThat(refreshTokenRepository.count()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh")
    class Refresh {

        @Test
        void rotatesAndRevokesOldToken() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String original = loginAndExtractRefreshToken();

            final MvcResult result = mockMvc.perform(post(REFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshPayload(original)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ACCESS_TOKEN).isNotEmpty())
                .andExpect(jsonPath(JSON_REFRESH_TOKEN).isNotEmpty())
                .andReturn();

            final String rotated = readJsonField(result, FIELD_REFRESH_TOKEN);

            assertThat(refreshTokenRepository.findAll())
                .as("rotated token differs from original, only the successor stays active")
                .satisfies(rows -> {
                    assertThat(rotated).isNotEqualTo(original);
                    assertThat(rows).extracting(RefreshToken::getUserId).allMatch(id -> id == userId);
                    assertThat(activeTokenCountForUser(userId)).isEqualTo(1L);
                });
        }

        @Test
        void replayingRevokedTokenRevokesEntireChain() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String original = loginAndExtractRefreshToken();

            final MvcResult firstRotate = mockMvc.perform(post(REFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshPayload(original)))
                .andExpect(status().isOk())
                .andReturn();
            final String rotated = readJsonField(firstRotate, FIELD_REFRESH_TOKEN);

            mockMvc.perform(post(REFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshPayload(original)))
                .andExpect(status().isUnauthorized());

            assertThat(activeTokenCountForUser(userId)).isZero();

            mockMvc.perform(post(REFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshPayload(rotated)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String original = loginAndExtractRefreshToken();
            expireUserTokens(userId);

            mockMvc.perform(post(REFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshPayload(original)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsTokenForDeletedUser() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String original = loginAndExtractRefreshToken();
            userRepository.deleteById(userId);

            mockMvc.perform(post(REFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshPayload(original)))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /auth/logout")
    class Logout {

        @Test
        void revokesTokenAndPreventsSubsequentRefresh() throws Exception {
            final long userId = seedUser(USERNAME, EMAIL, PASSWORD);
            final String token = loginAndExtractRefreshToken();

            mockMvc.perform(post(LOGOUT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshPayload(token)))
                .andExpect(status().isNoContent());

            assertThat(activeTokenCountForUser(userId)).isZero();

            mockMvc.perform(post(REFRESH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshPayload(token)))
                .andExpect(status().isUnauthorized());
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
    private String loginAndExtractRefreshToken() throws Exception {
        final MvcResult result = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload(USERNAME, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        return readJsonField(result, FIELD_REFRESH_TOKEN);
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private String readJsonField(final MvcResult result, final String field) throws Exception {
        final JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get(field).asText();
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

    private String refreshPayload(final String token) throws JsonProcessingException {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_REFRESH_TOKEN, token);
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
