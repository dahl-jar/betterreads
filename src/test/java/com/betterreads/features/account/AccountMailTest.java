package com.betterreads.features.account;

import com.betterreads.features.session.RefreshTokenRepository;
import com.betterreads.mailoutbox.MailOutboxRepository;
import com.betterreads.ratelimit.RateLimitFilter;
import com.betterreads.testsupport.Accounts;
import com.betterreads.testsupport.ContainerizedTest;
import com.betterreads.users.UserRepository;

import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The mail-outbox worker is off ({@code mail.outbox.worker-enabled=false}) so enqueued rows
 * stay in the database and a test can read the plaintext token out of the payload without
 * racing a real send.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "auth.refresh-cookie.secure=true",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false"
})
abstract class AccountMailTest extends ContainerizedTest {

    protected static final String OLD_PASSWORD = "OldP4ssword!";

    protected static final String NEW_PASSWORD = "BrandN3wPass!";

    protected static final String MULTIBYTE_PASSWORD = "é".repeat(40);

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected EmailTokenRepository emailTokenRepository;

    @Autowired
    protected EmailVerificationService emailVerificationService;

    @Autowired
    protected MailOutboxRepository mailOutboxRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        mailOutboxRepository.deleteAll();
        emailTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM app_user");
        rateLimitFilter.reset();
    }

    protected long seedUser() {
        return Accounts.seedUser(userRepository, passwordEncoder, Accounts.USER, Accounts.USER_EMAIL, OLD_PASSWORD);
    }

    protected String storedHash(final long userId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
            "SELECT password_hash FROM app_user WHERE user_id = ?", String.class, userId));
    }

    protected boolean storedPasswordMatches(final long userId, final String rawPassword) {
        return passwordEncoder.matches(rawPassword, storedHash(userId));
    }
}
