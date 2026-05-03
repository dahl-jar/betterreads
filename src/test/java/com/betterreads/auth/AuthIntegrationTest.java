package com.betterreads.auth;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.jwt.JwtIssuer;
import com.betterreads.auth.ratelimit.RateLimitFilter;
import com.betterreads.auth.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=60",
    "jwt.refresh-expiration-days=30",
    "app.cors.allowed-origins=https://app.betterreads.example.com",
    "auth.refresh-cookie.secure=true"
})
@SuppressWarnings("PMD.TooManyStaticImports")
class AuthIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final String ME_URL = "/api/v1/auth/me";

    private static final String DEFAULT_USERNAME = "alice";

    private static final String DEFAULT_EMAIL = "alice@example.com";

    private static final String DEFAULT_PASSWORD = "Sup3rSecret!";

    private static final String IT_SECRET =
        "integration-test-secret-must-be-at-least-256-bits-long-padding-padding";

    private static final String IT_ISSUER = "betterreads-it";

    private static final String OTHER_USERNAME = "different";

    private static final String OTHER_EMAIL = "different@example.com";

    private static final String UNKNOWN_USERNAME = "ghost";

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String JSON_TOKEN = "$.accessToken";

    private static final String JSON_USER_USERNAME = "$.user.username";

    private static final String JSON_USER_EMAIL = "$.user.email";

    private static final String JSON_USERNAME = "$.username";

    private static final String JSON_EMAIL = "$.email";

    private static final String MIXED_CASE_EMAIL = "Alice@Example.COM";

    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private static final String XFF_HEADER = "X-Forwarded-For";

    private static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

    private static final String CSP_HEADER = "Content-Security-Policy";

    private static final String SWAGGER_INDEX = "/swagger-ui/index.html";

    private static final String ALLOWED_ORIGIN = "https://app.betterreads.example.com";

    private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

    private static final String ORIGIN_HEADER = "Origin";

    private static final String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";

    private static final String ALLOW_METHODS_HEADER = "Access-Control-Allow-Methods";

    private static final String REQUEST_METHOD_HEADER = "Access-Control-Request-Method";

    private static final String METHOD_POST = "POST";

    private static final String NOT_MATCHING_PASSWORD = "anything-not-matching";

    private static final String NOSNIFF_VALUE = "nosniff";

    private static final String FIELD_USERNAME = "username";

    private static final String FIELD_EMAIL = "email";

    private static final String FIELD_PASSWORD = "password";

    private static final String FIELD_IDENTIFIER = "identifier";

    private static final int LOGIN_RATE_LIMIT_BURST = 10;

    private static final int REGISTER_RATE_LIMIT_BURST = 5;

    private static final int OVER_BURST = 5;

    private static final String LONG_PASSWORD_73_BYTES =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

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
        userRepository.deleteAll();
        rateLimitFilter.reset();
    }

    @Nested
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        void createsUserAndReturnsToken() throws Exception {
            final String body = registerPayload(DEFAULT_USERNAME, DEFAULT_EMAIL, DEFAULT_PASSWORD);

            mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(JSON_TOKEN).isNotEmpty())
                .andExpect(jsonPath(JSON_USER_USERNAME).value(DEFAULT_USERNAME))
                .andExpect(jsonPath(JSON_USER_EMAIL).value(DEFAULT_EMAIL));

            assertThat(userRepository.findByUsername(DEFAULT_USERNAME))
                .isPresent()
                .get()
                .satisfies(stored -> {
                    assertThat(stored.getEmail()).isEqualTo(DEFAULT_EMAIL);
                    assertThat(stored.getPasswordHash()).isNotEqualTo(DEFAULT_PASSWORD);
                    assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, stored.getPasswordHash())).isTrue();
                });
        }

        @Test
        void rejectsDuplicateUsernameWithConflict() throws Exception {
            seedUser(DEFAULT_USERNAME, DEFAULT_EMAIL, DEFAULT_PASSWORD);
            final String body = registerPayload(DEFAULT_USERNAME, OTHER_EMAIL, DEFAULT_PASSWORD);

            mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        }

        @Test
        void rejectsDuplicateEmailWithConflict() throws Exception {
            seedUser(DEFAULT_USERNAME, DEFAULT_EMAIL, DEFAULT_PASSWORD);
            final String body = registerPayload(OTHER_USERNAME, DEFAULT_EMAIL, DEFAULT_PASSWORD);

            mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        }

        @ParameterizedTest(name = "rejects invalid {0} with 400")
        @CsvSource({
            "short password,        alice,             alice@example.com, short",
            "invalid email,         alice,             not-an-email,      Sup3rSecret!",
            "email-shaped username, bob@example.com,   bob@other.com,     Sup3rSecret!",
            "password over 72 bytes, alice,            alice@example.com, " + LONG_PASSWORD_73_BYTES
        })
        void rejectsInvalidFieldWithBadRequest(
            final String invalidField,
            final String username,
            final String email,
            final String password
        ) throws Exception {
            final String body = registerPayload(username, email, password);

            mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        void normalizesEmailToLowercaseOnRegister() throws Exception {
            final String body = registerPayload(DEFAULT_USERNAME, MIXED_CASE_EMAIL, DEFAULT_PASSWORD);

            mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

            assertThat(userRepository.findByUsername(DEFAULT_USERNAME))
                .get()
                .extracting(User::getEmail)
                .isEqualTo(DEFAULT_EMAIL);
        }

        @ParameterizedTest(name = "rejects {0} body with 400")
        @CsvSource({
            "truncated, '{\"username\": \"alice\"'",
            "empty,     ''"
        })
        void rejectsBadJsonBodyWithBadRequest(final String label, final String content) throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(content))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @BeforeEach
        void seed() {
            seedUser(DEFAULT_USERNAME, DEFAULT_EMAIL, DEFAULT_PASSWORD);
        }

        @Test
        void succeedsWithUsername() throws Exception {
            final String body = loginPayload(DEFAULT_USERNAME, DEFAULT_PASSWORD);

            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOKEN).isNotEmpty())
                .andExpect(jsonPath(JSON_USER_USERNAME).value(DEFAULT_USERNAME));
        }

        @Test
        void succeedsWithEmail() throws Exception {
            final String body = loginPayload(DEFAULT_EMAIL, DEFAULT_PASSWORD);

            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOKEN).isNotEmpty());
        }

        @Test
        void succeedsWithMixedCaseEmail() throws Exception {
            final String body = loginPayload(MIXED_CASE_EMAIL, DEFAULT_PASSWORD);

            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOKEN).isNotEmpty());
        }

        @Test
        void rejectsWrongPasswordWithUnauthorized() throws Exception {
            final String body = loginPayload(DEFAULT_USERNAME, "WrongPassword1!");

            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsUnknownUserWithUnauthorized() throws Exception {
            final String body = loginPayload(UNKNOWN_USERNAME, DEFAULT_PASSWORD);

            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /auth/me")
    class Me {

        @Test
        void apiIssuedTokenFromRegisterAuthenticatesMeRequest() throws Exception {
            final String registerBody = registerPayload(DEFAULT_USERNAME, DEFAULT_EMAIL, DEFAULT_PASSWORD);

            final String registerResponse = mockMvc.perform(
                    post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
            final String apiToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

            mockMvc.perform(get(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + apiToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_USERNAME).value(DEFAULT_USERNAME))
                .andExpect(jsonPath(JSON_EMAIL).value(DEFAULT_EMAIL));
        }

        @Test
        void rejectsRequestWithoutToken() throws Exception {
            mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsMalformedToken() throws Exception {
            mockMvc.perform(get(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + "not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            final long userId = seedUser(DEFAULT_USERNAME, DEFAULT_EMAIL, DEFAULT_PASSWORD);
            final JwtIssuer expiredIssuer = new JwtIssuer(IT_SECRET, IT_ISSUER, Duration.ofSeconds(-1));
            final String expiredToken = expiredIssuer.issue(userId);

            mockMvc.perform(get(ME_URL).header(AUTH_HEADER, BEARER_PREFIX + expiredToken))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Security headers")
    class SecurityHeaders {

        @Test
        void includesCoreSecurityHeadersOnEveryResponse() throws Exception {
            mockMvc.perform(get(ME_URL))
                .andExpect(header().string(CONTENT_TYPE_OPTIONS_HEADER, NOSNIFF_VALUE))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().exists(CSP_HEADER))
                .andExpect(header().exists("Permissions-Policy"));
        }

        @Test
        void apiCspIsStricterThanSwaggerCsp() throws Exception {
            mockMvc.perform(get(ME_URL))
                .andExpect(header().string(CSP_HEADER,
                    org.hamcrest.Matchers.containsString("default-src 'none'")));
        }
    }

    @Nested
    @DisplayName("Rate limiting")
    class RateLimit {

        @Test
        void blocksLoginWithTooManyAttemptsFromSameIp() throws Exception {
            final String body = loginPayload(UNKNOWN_USERNAME, NOT_MATCHING_PASSWORD);

            for (int i = 0; i < LOGIN_RATE_LIMIT_BURST; i++) {
                mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body));
            }

            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(RETRY_AFTER_HEADER));
        }

        @Test
        void blocksRegisterWithTooManyAttemptsFromSameIp() throws Exception {
            final String userPrefix = "user";
            for (int i = 0; i < REGISTER_RATE_LIMIT_BURST; i++) {
                final String username = userPrefix + i;
                final String body = registerPayload(username, username + "@example.com", DEFAULT_PASSWORD);
                mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body));
            }

            final String overflow = registerPayload("overflow", "overflow@example.com", DEFAULT_PASSWORD);
            mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(overflow))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(RETRY_AFTER_HEADER));
        }

        @Test
        void doesNotConsumeTokensForNonPostMethods() throws Exception {
            final String body = loginPayload(UNKNOWN_USERNAME, NOT_MATCHING_PASSWORD);

            for (int i = 0; i < LOGIN_RATE_LIMIT_BURST + OVER_BURST; i++) {
                mockMvc.perform(options(LOGIN_URL));
            }

            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rateLimitedResponseStillIncludesSecurityHeaders() throws Exception {
            final String body = loginPayload(UNKNOWN_USERNAME, NOT_MATCHING_PASSWORD);
            for (int i = 0; i < LOGIN_RATE_LIMIT_BURST; i++) {
                mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body));
            }

            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(CONTENT_TYPE_OPTIONS_HEADER, NOSNIFF_VALUE))
                .andExpect(header().exists(CSP_HEADER));
        }

        @Test
        void retryAfterHeaderIsAtLeastOneSecond() throws Exception {
            final String body = loginPayload(UNKNOWN_USERNAME, NOT_MATCHING_PASSWORD);
            for (int i = 0; i < LOGIN_RATE_LIMIT_BURST; i++) {
                mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body));
            }

            final String retryAfter = mockMvc.perform(
                    post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andReturn()
                .getResponse()
                .getHeader(RETRY_AFTER_HEADER);

            assertThat(retryAfter)
                .isNotNull()
                .satisfies(value -> assertThat(Long.parseLong(value)).isGreaterThanOrEqualTo(1L));
        }

        @Test
        @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
        void doesNotTrustForwardedForFromUntrustedClient() throws Exception {
            final String body = loginPayload(UNKNOWN_USERNAME, NOT_MATCHING_PASSWORD);

            for (int i = 0; i < LOGIN_RATE_LIMIT_BURST; i++) {
                mockMvc.perform(post(LOGIN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header(XFF_HEADER, "10.0.0." + i));
            }

            mockMvc.perform(post(LOGIN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header(XFF_HEADER, "10.0.0.99"))
                .andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    @DisplayName("Swagger UI")
    class SwaggerUi {

        @Test
        void isReachableWithoutAuth() throws Exception {
            mockMvc.perform(get(SWAGGER_INDEX))
                .andExpect(status().is2xxSuccessful());
        }

        @Test
        void hasRelaxedCspToAllowInlineScripts() throws Exception {
            mockMvc.perform(get(SWAGGER_INDEX))
                .andExpect(header().string(CSP_HEADER,
                    org.hamcrest.Matchers.containsString("'self'")));
        }
    }

    @Nested
    @DisplayName("CORS")
    class Cors {

        @Test
        void echoesAllowOriginForConfiguredOrigin() throws Exception {
            mockMvc.perform(get(ME_URL).header(ORIGIN_HEADER, ALLOWED_ORIGIN))
                .andExpect(header().string(ALLOW_ORIGIN_HEADER, ALLOWED_ORIGIN));
        }

        @Test
        void omitsAllowOriginForDisallowedOrigin() throws Exception {
            mockMvc.perform(get(ME_URL).header(ORIGIN_HEADER, DISALLOWED_ORIGIN))
                .andExpect(header().doesNotExist(ALLOW_ORIGIN_HEADER));
        }

        @Test
        void preflightForAllowedOriginReturnsAllowMethodsIncludingPost() throws Exception {
            mockMvc.perform(options(LOGIN_URL)
                    .header(ORIGIN_HEADER, ALLOWED_ORIGIN)
                    .header(REQUEST_METHOD_HEADER, METHOD_POST))
                .andExpect(status().isOk())
                .andExpect(header().string(ALLOW_ORIGIN_HEADER, ALLOWED_ORIGIN))
                .andExpect(header().string(ALLOW_METHODS_HEADER,
                    org.hamcrest.Matchers.containsString(METHOD_POST)));
        }
    }

    private long seedUser(final String username, final String email, final String rawPassword) {
        final User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user).getUserId();
    }

    private String registerPayload(
        final String username, final String email, final String password
    ) throws JsonProcessingException {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_USERNAME, username);
        node.put(FIELD_EMAIL, email);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }

    private String loginPayload(
        final String identifier, final String password
    ) throws JsonProcessingException {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put(FIELD_IDENTIFIER, identifier);
        node.put(FIELD_PASSWORD, password);
        return objectMapper.writeValueAsString(node);
    }
}
