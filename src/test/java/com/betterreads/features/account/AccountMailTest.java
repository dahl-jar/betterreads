package com.betterreads.features.account;

import com.betterreads.features.session.RefreshTokenRepository;
import com.betterreads.mailoutbox.MailOutboxRepository;
import com.betterreads.ratelimit.RateLimitFilter;
import com.betterreads.testsupport.ContainerizedTest;
import com.betterreads.users.UserRepository;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    protected UserRepository userRepository;

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
        userRepository.deleteAll();
        rateLimitFilter.reset();
    }
}
