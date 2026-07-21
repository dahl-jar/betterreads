package com.betterreads.mail.outbox;

import com.betterreads.support.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "jwt.expiration-minutes=60",
    "jwt.refresh-expiration-days=30",
    "mail.provider=logging",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false",
    "mail.outbox.max-attempts=3"
})
class MailOutboxWorkerIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int MAX_ATTEMPTS = 3;

    private static final Duration FIRST_RETRY_BACKOFF = Duration.ofMinutes(5);

    private static final String EMAIL = "darrow@example.com";

    private static final String IDEMPOTENCY_PREFIX = "outbox-";

    private static final String TRANSIENT_ERROR = "temporary";

    private static final String CLEARED_PAYLOAD = "{}";

    @Autowired
    private MailOutboxRepository repository;

    @Autowired
    private MailOutboxService outbox;

    @Autowired
    private MailOutboxWorker worker;

    @Autowired
    private ScriptedMailSender sender;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        sender.script.clear();
        sender.captured.clear();
    }

    @Test
    void successfulSendMarksRowAsSent() {
        outbox.enqueuePasswordReset(EMAIL, "tok-1");
        sender.script.add(SendOutcome.success());

        worker.drain();

        final MailOutbox row = onlyRow();
        assertThat(row)
            .satisfies(saved -> assertThat(saved.getSentAt()).isNotNull())
            .satisfies(saved -> assertThat(saved.getFailedAt()).isNull())
            .satisfies(saved -> assertThat(saved.getAttemptCount()).isEqualTo(1));
    }

    @Test
    void successfulSendClearsPayload() {
        final String secretToken = "secret-token-must-not-linger";
        outbox.enqueuePasswordReset(EMAIL, secretToken);
        sender.script.add(SendOutcome.success());

        worker.drain();

        final MailOutbox row = onlyRow();
        assertThat(row.getPayload()).isEqualTo(CLEARED_PAYLOAD);
    }

    @Test
    void retryableFailureSchedulesNextAttemptAndKeepsRowPending() {
        final String retryToken = "tok-2";
        outbox.enqueuePasswordReset(EMAIL, retryToken);
        sender.script.add(SendOutcome.retryable(TRANSIENT_ERROR));

        final Instant beforeDrain = Instant.now();
        worker.drain();
        final Instant afterDrain = Instant.now();

        final MailOutbox row = onlyRow();
        assertThat(row)
            .satisfies(saved -> assertThat(saved.getSentAt()).isNull())
            .satisfies(saved -> assertThat(saved.getFailedAt()).isNull())
            .satisfies(saved -> assertThat(saved.getAttemptCount()).isEqualTo(1))
            .satisfies(saved -> assertThat(saved.getNextAttemptAt()).isBetween(
                beforeDrain.plus(FIRST_RETRY_BACKOFF), afterDrain.plus(FIRST_RETRY_BACKOFF)))
            .satisfies(saved -> assertThat(saved.getLastError()).contains(TRANSIENT_ERROR))
            .satisfies(saved -> assertThat(saved.getPayload()).contains(retryToken));
    }

    @Test
    void nonRetryableFailureMarksRowFailedImmediately() {
        outbox.enqueuePasswordReset(EMAIL, "tok-3");
        sender.script.add(SendOutcome.nonRetryable("400 Bad Request"));

        worker.drain();

        final MailOutbox row = onlyRow();
        assertThat(row)
            .satisfies(saved -> assertThat(saved.getFailedAt()).isNotNull())
            .satisfies(saved -> assertThat(saved.getSentAt()).isNull())
            .satisfies(saved -> assertThat(saved.getAttemptCount()).isEqualTo(1))
            .satisfies(saved -> assertThat(saved.getPayload()).isEqualTo(CLEARED_PAYLOAD));
    }

    @Test
    void retryableFailureMarksRowFailedAtMaxAttempts() {
        outbox.enqueuePasswordReset(EMAIL, "tok-4");
        sender.script.add(SendOutcome.retryable("retry 1"));
        sender.script.add(SendOutcome.retryable("retry 2"));
        sender.script.add(SendOutcome.retryable("retry 3"));

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            forceClaimable();
            worker.drain();
        }

        final MailOutbox row = onlyRow();
        assertThat(row)
            .satisfies(saved -> assertThat(saved.getAttemptCount()).isEqualTo(MAX_ATTEMPTS))
            .satisfies(saved -> assertThat(saved.getFailedAt()).isNotNull())
            .satisfies(saved -> assertThat(saved.getSentAt()).isNull())
            .satisfies(saved -> assertThat(saved.getPayload()).isEqualTo(CLEARED_PAYLOAD));
    }

    @Test
    void idempotencyKeyStaysStableAcrossRetries() {
        outbox.enqueuePasswordReset(EMAIL, "tok-5");
        sender.script.add(SendOutcome.retryable("first"));
        sender.script.add(SendOutcome.success());

        worker.drain();
        forceClaimable();
        worker.drain();

        final MailOutbox row = onlyRow();
        final long outboxId = row.getMailOutboxId();
        assertThat(sender.captured)
            .hasSize(2)
            .allSatisfy(message -> assertThat(message.idempotencyKey())
                .isEqualTo(IDEMPOTENCY_PREFIX + outboxId));
    }

    private MailOutbox onlyRow() {
        final List<MailOutbox> rows = repository.findAll();
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private void forceClaimable() {
        final MailOutbox row = onlyRow();
        if (row.getSentAt() == null && row.getFailedAt() == null) {
            row.setNextAttemptAt(Instant.now().minusSeconds(1));
            repository.save(row);
        }
    }

    @TestConfiguration
    static class SenderConfig {
        @Bean
        @Primary
        ScriptedMailSender scriptedMailSender() {
            return new ScriptedMailSender();
        }
    }

    static final class ScriptedMailSender implements MailSender {

        private final List<SendOutcome> script = new ArrayList<>();

        private final List<MailMessage> captured = new ArrayList<>();

        @Override
        public void send(final MailMessage message) {
            captured.add(message);
            if (script.isEmpty()) {
                throw new IllegalStateException("script exhausted; test setup mismatch");
            }
            final SendOutcome outcome = script.remove(0);
            outcome.apply();
        }
    }

    record SendOutcome(boolean shouldThrow, boolean retryable, String error) {
        static SendOutcome success() {
            return new SendOutcome(false, false, "");
        }

        static SendOutcome retryable(final String error) {
            return new SendOutcome(true, true, error);
        }

        static SendOutcome nonRetryable(final String error) {
            return new SendOutcome(true, false, error);
        }

        void apply() {
            if (shouldThrow) {
                throw new MailSendException(error, retryable);
            }
        }
    }
}
