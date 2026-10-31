package com.betterreads.features.session;

import com.betterreads.mailoutbox.MailOutboxRepository;
import com.betterreads.mailoutbox.MailOutboxService;
import com.betterreads.ratelimit.RateLimitFilter;
import com.betterreads.testsupport.Accounts;
import com.betterreads.testsupport.ConcurrentCalls;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "mail.outbox.worker-enabled=false",
    "auth.rate-limit.register-capacity=1000",
    "auth.rate-limit.register-refill-tokens=1000",
    "auth.rate-limit.register-refill-seconds=1"
})
// PMD.TooManyMethods: one test per way a register race ends.
@SuppressWarnings("PMD.TooManyMethods")
class RegisterRaceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String JSON_DETAIL = "$.detail";

    private static final String USERNAME_TAKEN = "Username already taken";

    private static final int SEED_HASH_LENGTH = 60;

    private static final String SHORT_HASH = "short";

    private static final long BARRIER_TIMEOUT_SECONDS = 10;

    private static final int NOTICE_RACE_THREADS = 8;

    private static final int CREATED = 201;

    private static final int CONFLICT = 409;

    @Autowired
    private HookedPasswordEncoder passwordEncoder;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        passwordEncoder.afterEncode.set(UnaryOperator.identity());
        passwordEncoder.encodeCalls.set(0);
        mailOutboxRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM app_user");
        rateLimitFilter.reset();
    }

    @Test
    void shouldEncodePasswordOnceWhenEmailTaken() throws Exception {
        insertUser(Accounts.USER, Accounts.USER_EMAIL, false);

        AuthRequests.register(mockMvc, body(Accounts.OTHER_USER, Accounts.USER_EMAIL))
            .andExpect(status().isCreated());

        assertThat(passwordEncoder.encodeCalls).hasValue(1);
    }

    @Test
    void shouldAnswerCreatedToBothWhenRaceOnEmail() throws Exception {
        final List<String> bodies = List.of(
            body(Accounts.USER, Accounts.USER_EMAIL),
            body(Accounts.OTHER_USER, Accounts.USER_EMAIL));

        final List<MvcResult> results = race(bodies);

        assertThat(statuses(results)).containsExactly(CREATED, CREATED);
        assertThat(rawUserCount()).isOne();
        assertThat(mailCount(MailOutboxService.TEMPLATE_EMAIL_VERIFICATION)).isOne();
        assertThat(mailCount(MailOutboxService.TEMPLATE_SIGNUP_ATTEMPT)).isOne();
        assertThat(unconsumedTokenCount()).isOne();
    }

    @Test
    void shouldAnswerConflictToLoserWhenRaceOnUsername() throws Exception {
        final List<String> bodies = List.of(
            body(Accounts.USER, Accounts.USER_EMAIL),
            body(Accounts.USER, Accounts.OTHER_USER_EMAIL));

        final List<MvcResult> results = race(bodies);

        assertThat(statuses(results)).containsExactly(CREATED, CONFLICT);
        jsonPath(JSON_DETAIL).value(USERNAME_TAKEN).match(loser(results));
        assertThat(rawUserCount()).isOne();
    }

    @Test
    void shouldAnswerConflictToLoserWhenRaceOnBothFields() throws Exception {
        final List<String> bodies = List.of(
            body(Accounts.USER, Accounts.USER_EMAIL),
            body(Accounts.USER, Accounts.USER_EMAIL));

        final List<MvcResult> results = race(bodies);

        assertThat(statuses(results)).containsExactly(CREATED, CONFLICT);
    }

    @Test
    void shouldQueueOneNoticeWhenRaceOnVerifiedEmail() throws Exception {
        insertUser(Accounts.USER, Accounts.USER_EMAIL, false);
        final List<String> bodies = IntStream.range(0, NOTICE_RACE_THREADS)
            .mapToObj(index -> body(Accounts.OTHER_USER + index, Accounts.USER_EMAIL))
            .toList();

        final List<MvcResult> results = race(bodies);

        assertThat(statuses(results)).hasSize(NOTICE_RACE_THREADS).containsOnly(CREATED);
        assertThat(mailCount(MailOutboxService.TEMPLATE_SIGNUP_ATTEMPT)).isOne();
    }

    @Test
    void shouldRejectSoftDeletedUsernameTakenAfterPreCheck() throws Exception {
        passwordEncoder.afterEncode.set(insertingDeletedUser(Accounts.USER, Accounts.OTHER_USER_EMAIL));

        AuthRequests.register(mockMvc, body(Accounts.USER, Accounts.USER_EMAIL))
            .andExpect(status().isConflict())
            .andExpect(jsonPath(JSON_DETAIL).value(USERNAME_TAKEN));

        assertThat(rawUserCount()).isOne();
    }

    @Test
    void shouldAnswerCreatedWithoutMailWhenEmailEntersGraceAfterPreCheck() throws Exception {
        passwordEncoder.afterEncode.set(insertingDeletedUser(Accounts.OTHER_USER, Accounts.USER_EMAIL));

        AuthRequests.register(mockMvc, body(Accounts.USER, Accounts.USER_EMAIL))
            .andExpect(status().isCreated());

        assertThat(rawUserCount()).isOne();
        assertThat(mailOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void shouldAnswerServerErrorWhenInsertFailsWithoutClash() throws Exception {
        passwordEncoder.afterEncode.set(hash -> SHORT_HASH);

        AuthRequests.register(mockMvc, body(Accounts.USER, Accounts.USER_EMAIL))
            .andExpect(status().isInternalServerError());

        assertThat(rawUserCount()).isZero();
    }

    private String body(final String username, final String email) {
        return Accounts.registerPayload(objectMapper, username, email, Accounts.PASSWORD);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private List<MvcResult> race(final List<String> bodies) throws Exception {
        passwordEncoder.afterEncode.set(waitForAll(bodies.size()));
        final AtomicInteger next = new AtomicInteger();
        return ConcurrentCalls.run(bodies.size(),
            () -> AuthRequests.register(mockMvc, bodies.get(next.getAndIncrement())).andReturn());
    }

    private static List<Integer> statuses(final List<MvcResult> results) {
        return results.stream()
            .map(result -> result.getResponse().getStatus())
            .sorted()
            .toList();
    }

    private static MvcResult loser(final List<MvcResult> results) {
        return results.stream()
            .filter(result -> result.getResponse().getStatus() == CONFLICT)
            .findFirst()
            .orElseThrow();
    }

    private static UnaryOperator<String> waitForAll(final int parties) {
        final CyclicBarrier barrier = new CyclicBarrier(parties);
        return hash -> {
            try {
                barrier.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return hash;
            } catch (final InterruptedException | BrokenBarrierException | TimeoutException ex) {
                throw new IllegalStateException("register threads did not all reach the password hook", ex);
            }
        };
    }

    private UnaryOperator<String> insertingDeletedUser(final String username, final String email) {
        return hash -> {
            insertUser(username, email, true);
            return hash;
        };
    }

    private void insertUser(final String username, final String email, final boolean deleted) {
        jdbcTemplate.update("""
            INSERT INTO app_user (username, email, password_hash, email_verified_at, deleted_at)
            VALUES (?, ?, ?, now(), CASE WHEN ? THEN now() END)
            """, username, email, "x".repeat(SEED_HASH_LENGTH), deleted);
    }

    private int rawUserCount() {
        return Objects.requireNonNull(jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class));
    }

    private int mailCount(final String template) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM mail_outbox WHERE template = ?", Integer.class, template));
    }

    private int unconsumedTokenCount() {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM email_token WHERE consumed_at IS NULL", Integer.class));
    }

    @TestConfiguration
    static class PasswordEncoderConfig {
        @Bean
        @Primary
        HookedPasswordEncoder hookedPasswordEncoder() {
            return new HookedPasswordEncoder();
        }
    }

    static final class HookedPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate = new BCryptPasswordEncoder();

        private final AtomicInteger encodeCalls = new AtomicInteger();

        private final AtomicReference<UnaryOperator<String>> afterEncode =
            new AtomicReference<>(UnaryOperator.identity());

        @Override
        public String encode(@Nullable final CharSequence rawPassword) {
            encodeCalls.incrementAndGet();
            return afterEncode.get().apply(Objects.requireNonNull(delegate.encode(rawPassword)));
        }

        @Override
        public boolean matches(@Nullable final CharSequence rawPassword, @Nullable final String encodedPassword) {
            return delegate.matches(rawPassword, encodedPassword);
        }
    }
}
