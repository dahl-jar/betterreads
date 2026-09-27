package com.betterreads.mailoutbox;

import com.betterreads.clients.mail.MailMessage;
import com.betterreads.clients.mail.MailSendException;
import com.betterreads.clients.mail.MailSender;
import com.betterreads.testsupport.ContainerizedTest;
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
    "mail.provider=logging",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false",
    "mail.outbox.max-attempts=3",
    "mail.outbox.claim-batch-size=2"
})
// PMD.TooManyMethods: one test per way a row ends, sent, retried, failed or unrenderable.
@SuppressWarnings("PMD.TooManyMethods")
class MailOutboxWorkerIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int MAX_ATTEMPTS = 3;

    private static final Duration FIRST_RETRY_BACKOFF = Duration.ofMinutes(5);

    private static final Duration LATER_RETRY_BACKOFF = Duration.ofMinutes(30);

    private static final int CLAIM_BATCH_SIZE = 2;

    private static final String EMAIL = "darrow@example.com";

    private static final String IDEMPOTENCY_PREFIX = "outbox-";

    private static final String TRANSIENT_ERROR = "temporary";

    private static final String CLEARED_PAYLOAD = "{}";

    private static final String APP_BASE_URL = "https://test.example.com";

    @Autowired
    private MailOutboxRepository repository;

    @Autowired
    private MailOutboxService outbox;

    @Autowired
    private MailOutboxWorker worker;

    @Autowired
    private MailOutboxClaimer claimer;

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
    void retryableFailureSchedulesNextAttempt() {
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
    void shouldWaitThirtyMinutesBeforeThirdAttempt() {
        outbox.enqueuePasswordReset(EMAIL, "tok-7");
        sender.script.add(SendOutcome.retryable(TRANSIENT_ERROR));
        sender.script.add(SendOutcome.retryable(TRANSIENT_ERROR));
        worker.drain();
        forceClaimable();

        final Instant beforeDrain = Instant.now();
        worker.drain();
        final Instant afterDrain = Instant.now();

        final MailOutbox row = onlyRow();
        assertThat(row.getNextAttemptAt())
            .isBetween(beforeDrain.plus(LATER_RETRY_BACKOFF), afterDrain.plus(LATER_RETRY_BACKOFF));
    }

    @Test
    void shouldClaimAtMostBatchSizeRowsPerDrain() {
        outbox.enqueuePasswordReset(EMAIL, "tok-8");
        outbox.enqueuePasswordReset(EMAIL, "tok-9");
        outbox.enqueuePasswordReset(EMAIL, "tok-10");
        sender.script.add(SendOutcome.success());
        sender.script.add(SendOutcome.success());
        sender.script.add(SendOutcome.success());

        worker.drain();

        final List<MailOutbox> rows = repository.findAll();
        assertThat(sender.captured).hasSize(CLAIM_BATCH_SIZE);
        assertThat(rows)
            .filteredOn(row -> row.getAttemptCount() == 0)
            .singleElement()
            .satisfies(row -> assertThat(row.getSentAt()).isNull());
    }

    @Test
    void shouldNotReclaimRowWhileLeaseHolds() {
        outbox.enqueuePasswordReset(EMAIL, "tok-11");
        claimer.claimBatch();

        final List<Long> reclaimed = claimer.claimBatch();

        assertThat(reclaimed).isEmpty();
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

    @Test
    void aPasswordResetRowSendsTheResetMail() {
        final String token = "tok-reset";
        outbox.enqueuePasswordReset(EMAIL, token);
        sender.script.add(SendOutcome.success());

        worker.drain();

        final MailMessage sent = onlySentMessage();
        assertThat(sent)
            .satisfies(mail -> assertThat(mail.recipient()).isEqualTo(EMAIL))
            .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Reset your BetterReads password"))
            .satisfies(mail -> assertThat(mail.body())
                .contains(APP_BASE_URL + "/reset-password?token=" + token));
    }

    @Test
    void anEmailVerificationRowSendsTheVerificationMail() {
        final String token = "tok-verify";
        outbox.enqueueEmailVerification(EMAIL, token);
        sender.script.add(SendOutcome.success());

        worker.drain();

        final MailMessage sent = onlySentMessage();
        assertThat(sent)
            .satisfies(mail -> assertThat(mail.recipient()).isEqualTo(EMAIL))
            .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Confirm your BetterReads email"))
            .satisfies(mail -> assertThat(mail.body())
                .contains(APP_BASE_URL + "/verify-email?token=" + token));
    }

    @Test
    void shouldMarkRowFailedWhenPayloadHasNoToken() {
        saveRowWithoutToken();

        worker.drain();

        final MailOutbox row = onlyRow();
        assertThat(row)
            .satisfies(saved -> assertThat(saved.getFailedAt()).isNotNull())
            .satisfies(saved -> assertThat(saved.getSentAt()).isNull())
            .satisfies(saved -> assertThat(saved.getLastError()).contains("payload missing token"));
    }

    @Test
    void shouldSendRestOfBatchWhenOneRowCannotRender() {
        final String token = "tok-6";
        saveRowWithoutToken();
        outbox.enqueuePasswordReset(EMAIL, token);
        sender.script.add(SendOutcome.success());

        worker.drain();

        final MailMessage sent = onlySentMessage();
        assertThat(sent.body()).contains(token);
    }

    private MailOutbox saveRowWithoutToken() {
        final Instant due = Instant.now().minusSeconds(60);
        final MailOutbox row = new MailOutbox();
        row.setTemplate(MailOutboxService.TEMPLATE_PASSWORD_RESET);
        row.setRecipient(EMAIL);
        row.setPayload(CLEARED_PAYLOAD);
        row.setCreatedAt(due);
        row.setNextAttemptAt(due);
        return repository.save(row);
    }

    private MailOutbox onlyRow() {
        final List<MailOutbox> rows = repository.findAll();
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private MailMessage onlySentMessage() {
        assertThat(sender.captured).hasSize(1);
        return sender.captured.getFirst();
    }

    private void forceClaimable() {
        final MailOutbox row = onlyRow();
        row.setNextAttemptAt(Instant.now().minusSeconds(1));
        repository.save(row);
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
                throw new IllegalStateException("script exhausted, test setup mismatch");
            }
            script.removeFirst().apply();
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
                throw new MailSendException(error, retryable, null);
            }
        }
    }
}
