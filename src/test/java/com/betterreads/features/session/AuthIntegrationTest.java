package com.betterreads.features.session;

import com.betterreads.mailoutbox.MailOutboxRepository;
import com.betterreads.mailoutbox.MailOutboxService;
import com.betterreads.ratelimit.RateLimitFilter;
import com.betterreads.security.JwtIssuer;
import com.betterreads.security.JwtProperties;
import com.betterreads.testsupport.Accounts;
import com.betterreads.testsupport.ContainerizedTest;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static com.betterreads.testsupport.Accounts.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.refresh-cookie.secure=true",
    "mail.outbox.worker-enabled=false"
})
class AuthIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String UNKNOWN_USERNAME = "unknownuser";

    private static final String JSON_TOKEN = "$.data.accessToken";

    private static final String JSON_USER_USERNAME = "$.data.user.username";

    private static final String JSON_USERNAME = "$.data.username";

    private static final String JSON_EMAIL = "$.data.email";

    private static final String LOWERCASE_USERNAME = "thirduser";

    private static final String MIXED_CASE_USERNAME = "ThirdUser";

    private static final String PASSWORD_40_CHARS_80_BYTES =
        "éééééééééééééééééééééééééééééééééééééééé";

    private static final String JSON_DETAIL = "$.detail";

    private static final String SET_COOKIE = "Set-Cookie";

    private static final String WRONG_PASSWORD = "WrongPassword1!";

    private static final String USERNAME_TAKEN = "Username already taken";

    private static final String EMPTY_PAYLOAD = "{}";

    private static final Duration INSIDE_NOTICE_WINDOW = Duration.ofHours(23);

    private static final Duration AFTER_NOTICE_WINDOW = Duration.ofHours(25);

    private static final int LABEL_LENGTH_OVER_LIMIT = 58;

    private static final int LABEL_LENGTH_AT_LIMIT = 57;

    private static final int LOCAL_PART_LENGTH = 64;

    private static final int DOMAIN_LABEL_LENGTH = 63;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        mailOutboxRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM app_user");
        rateLimitFilter.reset();
    }

    @Nested
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        void shouldRegisterWithoutStartingASession() throws Exception {
            final String body =
                Accounts.registerPayload(objectMapper, Accounts.USER, Accounts.USER_EMAIL, PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(SET_COOKIE))
                .andExpect(content().string(""));

            assertThat(userRepository.findIdByUsername(Accounts.USER).flatMap(userRepository::findById))
                .isPresent()
                .get()
                .satisfies(stored -> {
                    assertThat(stored.getEmail()).isEqualTo(Accounts.USER_EMAIL);
                    assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
                });
        }

        @Test
        void rejectsDuplicateUsernameWithConflict() throws Exception {
            seedUser();
            final String body =
                Accounts.registerPayload(objectMapper, Accounts.USER, Accounts.OTHER_USER_EMAIL, PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(conflictWithDetail(USERNAME_TAKEN));
        }

        @Test
        void shouldRejectTakenUsernameWhenEmailAlsoTaken() throws Exception {
            seedUser();
            final String body =
                Accounts.registerPayload(objectMapper, Accounts.USER, Accounts.USER_EMAIL, PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(conflictWithDetail(USERNAME_TAKEN));

            assertThat(mailOutboxRepository.findAll()).isEmpty();
        }

        @Test
        void shouldRejectUsernameHeldBySoftDeletedUserWithConflict() throws Exception {
            final User deleted = new User();
            deleted.setUsername(Accounts.USER);
            deleted.setEmail(Accounts.USER_EMAIL);
            deleted.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(PASSWORD)));
            deleted.setDeletedAt(Instant.now());
            userRepository.save(deleted);
            final String body =
                Accounts.registerPayload(objectMapper, Accounts.USER, Accounts.OTHER_USER_EMAIL, PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(conflictWithDetail(USERNAME_TAKEN));
        }

        @Test
        void shouldRejectEmailOverStoredLimit() throws Exception {
            final String body = Accounts.registerPayload(
                objectMapper, Accounts.USER, emailEndingIn(LABEL_LENGTH_OVER_LIMIT), PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldAcceptEmailAtStoredLimit() throws Exception {
            final String email = emailEndingIn(LABEL_LENGTH_AT_LIMIT);
            final String body = Accounts.registerPayload(objectMapper, Accounts.USER, email, PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(status().isCreated());

            assertThat(storedEmail()).isEqualTo(email);
        }

        @ParameterizedTest(name = "rejects invalid {0} with 400")
        @CsvSource({
            "short password,        user,                    user@example.com,    short",
            "invalid email,         user,                    not-an-email,        Sup3rSecret!",
            "email-shaped username, otheruser@example.com,   otheruser@example.com, Sup3rSecret!",
            "password over 72 bytes, user,                   user@example.com, " + PASSWORD_40_CHARS_80_BYTES
        })
        void rejectsInvalidFieldWithBadRequest(
            final String invalidField,
            final String username,
            final String email,
            final String password
        ) throws Exception {
            final String body = Accounts.registerPayload(objectMapper, username, email, password);

            AuthRequests.register(mockMvc, body)
                .andExpect(status().isBadRequest());
        }

        @Test
        void normalizesEmailToLowercaseOnRegister() throws Exception {
            final String body =
                Accounts.registerPayload(objectMapper, Accounts.USER, Accounts.MIXED_CASE_EMAIL, PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(status().isCreated());

            assertThat(storedEmail()).isEqualTo(Accounts.USER_EMAIL);
        }

        @Test
        void rejectsUsernameDifferingOnlyByCaseWithConflict() throws Exception {
            Accounts.seedUser(userRepository, passwordEncoder, LOWERCASE_USERNAME, Accounts.USER_EMAIL, PASSWORD);
            final String body =
                Accounts.registerPayload(objectMapper, MIXED_CASE_USERNAME, Accounts.OTHER_USER_EMAIL, PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(conflictWithDetail(USERNAME_TAKEN));
        }

        @Test
        void preservesUsernameCaseOnRegister() throws Exception {
            final String body =
                Accounts.registerPayload(objectMapper, MIXED_CASE_USERNAME, Accounts.USER_EMAIL, PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(status().isCreated());

            assertThat(userRepository.findIdByUsername(MIXED_CASE_USERNAME).flatMap(userRepository::findById))
                .get()
                .extracting(User::getUsername)
                .isEqualTo(MIXED_CASE_USERNAME);
        }

        @Test
        void rejectsBadJsonBodyWithBadRequest() throws Exception {
            AuthRequests.register(mockMvc, "{\"username\": \"user\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_DETAIL).value("Malformed request body"));
        }
    }

    @Nested
    @DisplayName("POST /auth/register with a taken email")
    class RegisterTakenEmail {

        @Test
        void shouldAnswerLikeNewEmailWhenEmailTaken() throws Exception {
            final long userId = seedUser();
            final User seeded = userRepository.findById(userId).orElseThrow();
            final int credentialVersion = seeded.getCredentialVersion();
            final String body =
                Accounts.registerPayload(objectMapper, Accounts.OTHER_USER, Accounts.USER_EMAIL, WRONG_PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(SET_COOKIE))
                .andExpect(content().string(""));

            final User stored = userRepository.findById(userId).orElseThrow();
            assertThat(userRepository.count()).isOne();
            assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
            assertThat(stored.getUsername()).isEqualTo(Accounts.USER);
            assertThat(stored.getCredentialVersion()).isEqualTo(credentialVersion);
        }

        @Test
        void shouldQueueNoticeWhenEmailTakenByVerifiedAccount() throws Exception {
            seedUser();

            AuthRequests.register(mockMvc, takenEmailBody())
                .andExpect(status().isCreated());

            assertThat(mailOutboxRepository.findAll())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getTemplate()).isEqualTo(MailOutboxService.TEMPLATE_SIGNUP_ATTEMPT);
                    assertThat(row.getRecipient()).isEqualTo(Accounts.USER_EMAIL);
                    assertThat(row.getPayload()).isEqualTo(EMPTY_PAYLOAD);
                });
        }

        @Test
        void shouldSkipNoticeOnRepeat() throws Exception {
            seedUser();
            AuthRequests.register(mockMvc, takenEmailBody())
                .andExpect(status().isCreated());

            AuthRequests.register(mockMvc, takenEmailBody())
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

            assertThat(mailOutboxRepository.findAll()).hasSize(1);
        }

        @Test
        void shouldSkipNoticeInsideWindow() throws Exception {
            registerWithNoticeSent(INSIDE_NOTICE_WINDOW);

            assertThat(mailOutboxRepository.findAll()).isEmpty();
        }

        @Test
        void shouldQueueNoticeAfterWindow() throws Exception {
            registerWithNoticeSent(AFTER_NOTICE_WINDOW);

            assertThat(mailOutboxRepository.findAll()).hasSize(1);
        }

        @Test
        void shouldQueueNoticeToStoredAddressWhenEmailMixedCase() throws Exception {
            seedUser();
            final String body =
                Accounts.registerPayload(objectMapper, Accounts.OTHER_USER, Accounts.MIXED_CASE_EMAIL, PASSWORD);

            AuthRequests.register(mockMvc, body)
                .andExpect(status().isCreated());

            assertThat(mailOutboxRepository.findAll())
                .singleElement()
                .satisfies(row -> assertThat(row.getRecipient()).isEqualTo(Accounts.USER_EMAIL));
        }

        @Test
        void shouldAnswerBadRequestWhenPasswordTooLongOnTakenEmail() throws Exception {
            seedUser();
            final String body = Accounts.registerPayload(
                objectMapper, Accounts.OTHER_USER, Accounts.USER_EMAIL, PASSWORD_40_CHARS_80_BYTES);

            AuthRequests.register(mockMvc, body)
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @BeforeEach
        void seed() {
            seedUser();
        }

        @Test
        void succeedsWithUsername() throws Exception {
            final String body = Accounts.loginPayload(objectMapper, Accounts.USER, PASSWORD);

            AuthRequests.login(mockMvc, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOKEN).isNotEmpty())
                .andExpect(jsonPath(JSON_USER_USERNAME).value(Accounts.USER));
        }

        @Test
        void succeedsWithUsernameInDifferentCase() throws Exception {
            Accounts.seedUser(userRepository, passwordEncoder, LOWERCASE_USERNAME, Accounts.OTHER_USER_EMAIL, PASSWORD);
            final String body = Accounts.loginPayload(objectMapper, MIXED_CASE_USERNAME, PASSWORD);

            AuthRequests.login(mockMvc, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOKEN).isNotEmpty())
                .andExpect(jsonPath(JSON_USER_USERNAME).value(LOWERCASE_USERNAME));
        }

        @Test
        void succeedsWithEmail() throws Exception {
            final String body = Accounts.loginPayload(objectMapper, Accounts.USER_EMAIL, PASSWORD);

            AuthRequests.login(mockMvc, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOKEN).isNotEmpty());
        }

        @Test
        void succeedsWithMixedCaseEmail() throws Exception {
            final String body = Accounts.loginPayload(objectMapper, Accounts.MIXED_CASE_EMAIL, PASSWORD);

            AuthRequests.login(mockMvc, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOKEN).isNotEmpty());
        }

        @Test
        void shouldLogInWithIdentifierSurroundedByWhitespace() throws Exception {
            final String body = Accounts.loginPayload(objectMapper, " " + Accounts.USER + " ", PASSWORD);

            AuthRequests.login(mockMvc, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_USER_USERNAME).value(Accounts.USER));
        }

        @Test
        void rejectsWrongPasswordWithUnauthorized() throws Exception {
            final String body = Accounts.loginPayload(objectMapper, Accounts.USER, WRONG_PASSWORD);

            AuthRequests.login(mockMvc, body)
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsUnknownUserWithUnauthorized() throws Exception {
            final String body = Accounts.loginPayload(objectMapper, UNKNOWN_USERNAME, PASSWORD);

            AuthRequests.login(mockMvc, body)
                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldRejectLoginUntilTheEmailIsVerified() throws Exception {
            registerOtherUser();
            final String body = Accounts.loginPayload(objectMapper, Accounts.OTHER_USER, PASSWORD);

            AuthRequests.login(mockMvc, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(JSON_DETAIL).value("Verify your email to log in"))
                .andExpect(header().doesNotExist(SET_COOKIE));
        }

        @Test
        void shouldAnswerUnauthorizedForAWrongPasswordOnAnUnverifiedAccount() throws Exception {
            registerOtherUser();
            final String body = Accounts.loginPayload(objectMapper, Accounts.OTHER_USER, WRONG_PASSWORD);

            AuthRequests.login(mockMvc, body)
                .andExpect(status().isUnauthorized());
        }

        // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
        @SuppressWarnings("PMD.SignatureDeclareThrowsException")
        private void registerOtherUser() throws Exception {
            final String body =
                Accounts.registerPayload(objectMapper, Accounts.OTHER_USER, Accounts.OTHER_USER_EMAIL, PASSWORD);
            AuthRequests.register(mockMvc, body)
                .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("GET /auth/me")
    class Me {

        @Test
        void shouldAuthenticateMeWithTheTokenFromLogin() throws Exception {
            seedUser();
            final String loginBody = Accounts.loginPayload(objectMapper, Accounts.USER, PASSWORD);

            final MvcResult loginResult = AuthRequests.login(mockMvc, loginBody)
                .andExpect(status().isOk())
                .andReturn();
            final String apiToken = Accounts.accessTokenOf(objectMapper, loginResult);

            mockMvc.perform(get(Accounts.ME_URL).header(Accounts.AUTH_HEADER, Accounts.BEARER_PREFIX + apiToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_USERNAME).value(Accounts.USER))
                .andExpect(jsonPath(JSON_EMAIL).value(Accounts.USER_EMAIL));
        }

        @Test
        void rejectsRequestWithoutToken() throws Exception {
            mockMvc.perform(get(Accounts.ME_URL))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsMalformedToken() throws Exception {
            mockMvc.perform(get(Accounts.ME_URL)
                    .header(Accounts.AUTH_HEADER, Accounts.BEARER_PREFIX + "not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsExpiredToken() throws Exception {
            final long userId = seedUser();
            final User user = userRepository.findById(userId).orElseThrow();
            final int credentialVersion = user.getCredentialVersion();
            final JwtIssuer expiredIssuer =
                new JwtIssuer(jwtProperties.secret(), jwtProperties.issuer(), Duration.ofSeconds(-1));
            final String expiredToken = expiredIssuer.issue(userId, credentialVersion);

            mockMvc.perform(get(Accounts.ME_URL).header(Accounts.AUTH_HEADER, Accounts.BEARER_PREFIX + expiredToken))
                .andExpect(status().isUnauthorized());
        }
    }

    private long seedUser() {
        return Accounts.seedUser(userRepository, passwordEncoder, Accounts.USER, Accounts.USER_EMAIL, PASSWORD);
    }

    private String takenEmailBody() {
        return Accounts.registerPayload(objectMapper, Accounts.OTHER_USER, Accounts.USER_EMAIL, PASSWORD);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void registerWithNoticeSent(final Duration ago) throws Exception {
        seedUser();
        jdbcTemplate.update(
            "UPDATE app_user SET signup_notice_sent_at = now() - make_interval(hours => ?::int) WHERE email = ?",
            ago.toHours(), Accounts.USER_EMAIL);

        AuthRequests.register(mockMvc, takenEmailBody())
            .andExpect(status().isCreated())
            .andExpect(content().string(""));
    }

    private String storedEmail() {
        return userRepository.findIdByUsername(Accounts.USER)
            .flatMap(userRepository::findById)
            .orElseThrow()
            .getEmail();
    }

    private static String emailEndingIn(final int lastLabelLength) {
        return "a".repeat(LOCAL_PART_LENGTH) + "@" + "b".repeat(DOMAIN_LABEL_LENGTH) + "."
            + "b".repeat(DOMAIN_LABEL_LENGTH) + "." + "b".repeat(lastLabelLength) + ".com";
    }

    private static ResultMatcher conflictWithDetail(final String detail) {
        return result -> {
            status().isConflict().match(result);
            jsonPath(JSON_DETAIL).value(detail).match(result);
        };
    }
}
