package com.betterreads.features.account;

import com.betterreads.security.RefreshCookies;
import com.betterreads.testsupport.Accounts;
import com.betterreads.testsupport.UserLockRace;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.Cookie;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.betterreads.testsupport.Accounts.AUTH_HEADER;
import static com.betterreads.testsupport.Accounts.BEARER_PREFIX;
import static com.betterreads.testsupport.Accounts.LOGIN_URL;
import static com.betterreads.testsupport.Accounts.REFRESH_URL;
import static com.betterreads.testsupport.Accounts.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordChangeIntegrationTest extends AccountMailTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String CHANGE_PASSWORD_URL = "/api/v1/auth/me/password";

    private static final String WRONG_PASSWORD = "Wr0ngPassw0rd!";

    private static final String SEVEN_CHARACTERS = "Passw0r";

    private static final String WRONG_CURRENT_DETAIL = "Current password is incorrect";

    private static final int CHANGE_PASSWORD_BURST = 5;

    private static final String INSERT_SUCCESSOR_SQL = """
        INSERT INTO refresh_token (user_id, token_hash, expires_at, persistent)
        VALUES (?, 'rotated-successor', now() + interval '1 day', false)
        """;

    private static final String ACTIVE_TOKEN_IDS_SQL =
        "SELECT refresh_token_id FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL";

    private static final String REVOKE_TOKEN_SQL =
        "UPDATE refresh_token SET revoked_at = now() WHERE refresh_token_id = ?";

    private static final String SET_PASSWORD_HASH_SQL = "UPDATE app_user SET password_hash = ? WHERE user_id = ?";

    @Nested
    @DisplayName("PUT /auth/me/password")
    class ChangePassword {

        @Test
        void shouldRejectAWrongCurrentPassword() throws Exception {
            final long userId = seedUser();
            final MvcResult session = login(OLD_PASSWORD);

            final ResultActions response = changePassword(session, WRONG_PASSWORD, NEW_PASSWORD);

            response
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(WRONG_CURRENT_DETAIL));
            assertThat(storedPasswordMatches(userId, OLD_PASSWORD)).isTrue();
        }

        @Test
        void shouldRejectANewPasswordUnderEightCharacters() throws Exception {
            final long userId = seedUser();
            final MvcResult session = login(OLD_PASSWORD);

            final ResultActions response = changePassword(session, OLD_PASSWORD, SEVEN_CHARACTERS);

            response.andExpect(status().isBadRequest());
            assertThat(storedPasswordMatches(userId, OLD_PASSWORD)).isTrue();
        }

        @Test
        void shouldRejectANewPasswordOverSeventyTwoBytes() throws Exception {
            final long userId = seedUser();
            final MvcResult session = login(OLD_PASSWORD);

            final ResultActions response = changePassword(session, OLD_PASSWORD, MULTIBYTE_PASSWORD);

            response.andExpect(status().isBadRequest());
            assertThat(storedPasswordMatches(userId, OLD_PASSWORD)).isTrue();
        }

        @Test
        void shouldRequireSignInToChangeThePassword() throws Exception {
            seedUser();

            final ResultActions response = mockMvc.perform(changePasswordRequest(OLD_PASSWORD, NEW_PASSWORD));

            response.andExpect(status().isUnauthorized());
        }

        @Test
        void shouldRateLimitPasswordChanges() throws Exception {
            seedUser();
            final MvcResult session = login(OLD_PASSWORD);
            for (int i = 0; i < CHANGE_PASSWORD_BURST; i++) {
                final ResultActions allowed = changePassword(session, WRONG_PASSWORD, NEW_PASSWORD);
                allowed.andExpect(status().isBadRequest());
            }

            final ResultActions response = changePassword(session, WRONG_PASSWORD, NEW_PASSWORD);

            response.andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    @DisplayName("Sessions after a password change")
    class Sessions {

        @Test
        void shouldEndOnlyTheOtherSessions() throws Exception {
            final long userId = seedUser();
            final MvcResult session = login(OLD_PASSWORD);
            login(OLD_PASSWORD);

            final ResultActions response = changePassword(session, OLD_PASSWORD, NEW_PASSWORD);

            response.andExpect(status().isNoContent());
            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isEqualTo(1L);
            final ResultActions refreshed = refresh(Accounts.refreshCookie(session));
            refreshed.andExpect(status().isOk());
        }

        @Test
        void shouldEndEverySessionWhenNoRefreshCookieIsSent() throws Exception {
            final long userId = seedUser();
            final MvcResult session = login(OLD_PASSWORD);

            final ResultActions response = changePasswordSendingCookie(session, null);

            response.andExpect(status().isNoContent());
            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isZero();
        }

        @Test
        void shouldEndEverySessionWhenTheRefreshCookieIsUnknown() throws Exception {
            final long userId = seedUser();
            final MvcResult session = login(OLD_PASSWORD);

            final ResultActions response = changePasswordSendingCookie(session, AccountTestFixture.UNKNOWN_TOKEN);

            response.andExpect(status().isNoContent());
            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isZero();
        }
    }

    @Nested
    @DisplayName("Access tokens after a password change")
    class AccessTokens {

        @Test
        void shouldRejectAnAccessTokenIssuedJustBeforeTheChange() throws Exception {
            seedUser();
            final MvcResult session = login(OLD_PASSWORD);
            final MvcResult otherSession = login(OLD_PASSWORD);
            changePassword(session, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

            final ResultActions response = currentUser(Accounts.accessTokenOf(objectMapper, otherSession));

            response.andExpect(status().isUnauthorized());
        }

        @Test
        void shouldAcceptAnAccessTokenIssuedAfterTheChange() throws Exception {
            seedUser();
            final MvcResult session = login(OLD_PASSWORD);
            changePassword(session, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());
            final MvcResult newSession = login(NEW_PASSWORD);

            final ResultActions response = currentUser(Accounts.accessTokenOf(objectMapper, newSession));

            response.andExpect(status().isOk());
        }

        @Test
        void shouldIssueAWorkingAccessTokenFromTheKeptRefreshCookie() throws Exception {
            seedUser();
            final MvcResult session = login(OLD_PASSWORD);
            changePassword(session, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

            final ResultActions refreshResponse = refresh(Accounts.refreshCookie(session));
            final MvcResult refreshed = refreshResponse.andReturn();

            final ResultActions response = currentUser(Accounts.accessTokenOf(objectMapper, refreshed));
            response.andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Password change racing another session")
    class SessionRace {

        @Test
        void shouldEndASessionRotatedDuringAPasswordChange() throws Exception {
            final long userId = seedUser();
            login(OLD_PASSWORD);
            final MvcResult session = login(OLD_PASSWORD);

            final ResultActions response = new UserLockRace(jdbcTemplate).race(
                userId,
                () -> jdbcTemplate.update(INSERT_SUCCESSOR_SQL, userId),
                () -> changePassword(session, OLD_PASSWORD, NEW_PASSWORD),
                inserted -> { });

            response.andExpect(status().isNoContent());
            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isEqualTo(1L);
        }

        @Test
        void shouldRefuseARefreshThatRacesAPasswordChange() throws Exception {
            final long userId = seedUser();
            final MvcResult session = login(OLD_PASSWORD);

            final ResultActions refreshed = new UserLockRace(jdbcTemplate).race(
                userId,
                () -> jdbcTemplate.queryForList(ACTIVE_TOKEN_IDS_SQL, Long.class, userId),
                () -> refresh(Accounts.refreshCookie(session)),
                activeIds -> activeIds.forEach(id -> jdbcTemplate.update(REVOKE_TOKEN_SQL, id)));

            refreshed.andExpect(status().isUnauthorized());
            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isZero();
        }

        @Test
        void shouldRefuseAPasswordChangeWhenTheAccountIsDeletedMidRequest() throws Exception {
            final long userId = seedUser();
            final MvcResult session = login(OLD_PASSWORD);
            final String hashBefore = storedHash(userId);

            final ResultActions response = new UserLockRace(jdbcTemplate).race(
                userId,
                () -> USER,
                () -> changePassword(session, OLD_PASSWORD, NEW_PASSWORD),
                username -> Accounts.softDelete(jdbcTemplate, username));

            response.andExpect(status().isUnauthorized());
            assertThat(storedHash(userId)).isEqualTo(hashBefore);
        }

        @Test
        void shouldRefuseALoginWithTheOldPasswordThatRacesAPasswordChange() throws Exception {
            final long userId = seedUser();

            final ResultActions loggedIn = new UserLockRace(jdbcTemplate).race(
                userId,
                () -> passwordEncoder.encode(NEW_PASSWORD),
                () -> mockMvc.perform(post(LOGIN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(Accounts.loginPayload(objectMapper, USER, OLD_PASSWORD))),
                newHash -> jdbcTemplate.update(SET_PASSWORD_HASH_SQL, newHash, userId));

            loggedIn.andExpect(status().isUnauthorized());
            assertThat(Accounts.activeRefreshTokenCount(refreshTokenRepository, userId)).isZero();
        }
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions changePasswordSendingCookie(final MvcResult session, final @Nullable String cookieValue)
        throws Exception {
        final MockHttpServletRequestBuilder request = changePasswordRequest(OLD_PASSWORD, NEW_PASSWORD)
            .header(AUTH_HEADER, BEARER_PREFIX + Accounts.accessTokenOf(objectMapper, session));
        if (cookieValue != null) {
            request.cookie(new Cookie(RefreshCookies.COOKIE_NAME, cookieValue));
        }
        return mockMvc.perform(request);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private MvcResult login(final String password) throws Exception {
        return AccountTestFixture.login(mockMvc, objectMapper, USER, password);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions changePassword(final MvcResult session, final String currentPassword,
        final String newPassword) throws Exception {
        return mockMvc.perform(changePasswordRequest(currentPassword, newPassword)
            .header(AUTH_HEADER, BEARER_PREFIX + Accounts.accessTokenOf(objectMapper, session))
            .cookie(new Cookie(RefreshCookies.COOKIE_NAME, Accounts.refreshCookie(session))));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions refresh(final String cookieValue) throws Exception {
        return mockMvc.perform(post(REFRESH_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .cookie(new Cookie(RefreshCookies.COOKIE_NAME, cookieValue)));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions currentUser(final String accessToken) throws Exception {
        return mockMvc.perform(get(Accounts.ME_URL).header(AUTH_HEADER, BEARER_PREFIX + accessToken));
    }

    private MockHttpServletRequestBuilder changePasswordRequest(final String currentPassword,
        final String newPassword) {
        return put(CHANGE_PASSWORD_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(changePasswordPayload(currentPassword, newPassword));
    }

    private String changePasswordPayload(final String currentPassword, final String newPassword) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("currentPassword", currentPassword);
        node.put("newPassword", newPassword);
        return objectMapper.writeValueAsString(node);
    }
}
