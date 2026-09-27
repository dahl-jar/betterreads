package com.betterreads.testsupport;

import com.betterreads.features.session.RefreshTokenRepository;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;

import java.net.HttpCookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public final class Accounts {

    public static final String PASSWORD = "Sup3rSecret!";

    public static final String USERNAME = "darrow";

    public static final String EMAIL = "darrow@example.com";

    public static final String MIXED_CASE_EMAIL = "Darrow@Example.COM";

    public static final String REGISTER_URL = "/api/v1/auth/register";

    public static final String LOGIN_URL = "/api/v1/auth/login";

    public static final String REFRESH_URL = "/api/v1/auth/refresh";

    public static final String ME_URL = "/api/v1/auth/me";

    private static final Duration EXPIRED_AGE = Duration.ofHours(2);

    private static final String FIELD_USERNAME = "username";

    private static final String FIELD_EMAIL = "email";

    private static final String FIELD_PASSWORD = "password";

    private static final String FIELD_IDENTIFIER = "identifier";

    private Accounts() {
    }

    public static long seedUser(final UserRepository users, final PasswordEncoder encoder,
        final String username, final String email, final String rawPassword) {
        final User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(Objects.requireNonNull(encoder.encode(rawPassword)));
        return users.save(user).getUserId();
    }

    public static String registerPayload(final ObjectMapper objectMapper, final String username,
        final String email, final String password) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_USERNAME, username);
        node.put(FIELD_EMAIL, email);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }

    public static String loginPayload(final ObjectMapper objectMapper, final String identifier,
        final String password) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_IDENTIFIER, identifier);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }

    public static String refreshCookie(final MvcResult result) {
        final String setCookie = Objects.requireNonNull(
            result.getResponse().getHeader(HttpHeaders.SET_COOKIE),
            "response is missing the Set-Cookie header");
        return HttpCookie.parse(setCookie).getFirst().getValue();
    }

    public static long activeRefreshTokenCount(final RefreshTokenRepository tokens, final long userId) {
        return tokens.findAllByUserIdAndRevokedAtIsNull(userId).size();
    }

    public static Instant expiredIssuedAt() {
        return Instant.now().minus(EXPIRED_AGE);
    }
}
