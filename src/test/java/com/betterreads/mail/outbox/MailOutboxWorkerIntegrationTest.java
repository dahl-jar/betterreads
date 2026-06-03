package com.betterreads.mail.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the outbox worker's claim/send/resolve loop end-to-end against real Postgres. The
 * scheduled poller is disabled; tests trigger {@code drain()} manually for deterministic
 * sequencing. A scripted {@link MailSender} substitutes for the real transport so tests can
 * force success / retryable-fail / permanent-fail without touching Resend.
 */
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
class MailOutboxWorkerIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;

    private static final String EMAIL = "alice@example.com";

    private static final String IDEMPOTENCY_PREFIX = "outbox-";

    private static final String TRANSIENT_ERROR = "temporary";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

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

        final MailOutbox row = repository.findAll().get(0);
        assertThat(row)
            .as("row is marked sent on first attempt and the send used the row id as idempotency key")
            .satisfies(r -> assertThat(r.getSentAt()).isNotNull())
            .satisfies(r -> assertThat(r.getFailedAt()).isNull())
            .satisfies(r -> assertThat(r.getAttemptCount()).isEqualTo(1))
            .satisfies(r -> assertThat(sender.captured)
                .hasSize(1)
                .first()
                .extracting(MailMessage::idempotencyKey)
                .isEqualTo(IDEMPOTENCY_PREFIX + r.getMailOutboxId()));
    }

    @Test
    void successfulSendClearsThePayload() {
        final String secretToken = "secret-token-must-not-linger";
        outbox.enqueuePasswordReset(EMAIL, secretToken);
        sender.script.add(SendOutcome.success());

        worker.drain();

        final MailOutbox row = repository.findAll().get(0);
        assertThat(row.getPayload())
            .as("payload must not retain the plaintext token after send so a DB read leak cannot redeem it")
            .doesNotContain(secretToken);
    }

    @Test
    void retryableFailureSchedulesNextAttemptAndKeepsRowPending() {
        outbox.enqueuePasswordReset(EMAIL, "tok-2");
        sender.script.add(SendOutcome.retryable(TRANSIENT_ERROR));

        worker.drain();

        assertThat(repository.findAll().get(0))
            .as("retryable failure leaves row pending with bumped next_attempt_at and recorded error")
            .satisfies(r -> assertThat(r.getSentAt()).isNull())
            .satisfies(r -> assertThat(r.getFailedAt()).isNull())
            .satisfies(r -> assertThat(r.getAttemptCount()).isEqualTo(1))
            .satisfies(r -> assertThat(r.getNextAttemptAt()).isAfter(Instant.now().plus(Duration.ofMinutes(1))))
            .satisfies(r -> assertThat(r.getLastError()).contains(TRANSIENT_ERROR));
    }

    @Test
    void nonRetryableFailureMarksRowFailedImmediately() {
        outbox.enqueuePasswordReset(EMAIL, "tok-3");
        sender.script.add(SendOutcome.nonRetryable("400 Bad Request"));

        worker.drain();

        assertThat(repository.findAll().get(0))
            .as("non-retryable failure marks the row failed on the first attempt")
            .satisfies(r -> assertThat(r.getFailedAt()).isNotNull())
            .satisfies(r -> assertThat(r.getSentAt()).isNull())
            .satisfies(r -> assertThat(r.getAttemptCount()).isEqualTo(1));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        outbox.enqueuePasswordReset(EMAIL, "tok-4");
        sender.script.add(SendOutcome.retryable("flap 1"));
        sender.script.add(SendOutcome.retryable("flap 2"));
        sender.script.add(SendOutcome.retryable("flap 3"));

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            forceClaimable();
            worker.drain();
        }

        assertThat(repository.findAll().get(0))
            .as("after max attempts the row is marked failed even though every attempt was retryable")
            .satisfies(r -> assertThat(r.getAttemptCount()).isEqualTo(MAX_ATTEMPTS))
            .satisfies(r -> assertThat(r.getFailedAt()).isNotNull())
            .satisfies(r -> assertThat(r.getSentAt()).isNull());
    }

    @Test
    void idempotencyKeyStaysStableAcrossRetries() {
        outbox.enqueuePasswordReset(EMAIL, "tok-5");
        sender.script.add(SendOutcome.retryable("first"));
        sender.script.add(SendOutcome.success());

        worker.drain();
        forceClaimable();
        worker.drain();

        final MailOutbox row = repository.findAll().get(0);
        final long outboxId = row.getMailOutboxId();
        assertThat(sender.captured)
            .as("idempotency key is the same on attempt 1 and attempt 2")
            .hasSize(2)
            .allSatisfy(msg -> assertThat(msg.idempotencyKey()).isEqualTo(IDEMPOTENCY_PREFIX + outboxId));
    }

    private void forceClaimable() {
        repository.findAll().forEach(row -> {
            if (row.getSentAt() == null && row.getFailedAt() == null) {
                row.setNextAttemptAt(Instant.now().minusSeconds(1));
                repository.save(row);
            }
        });
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
