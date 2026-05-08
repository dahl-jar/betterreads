package com.betterreads.mail.outbox;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Drains the {@code mail_outbox} table. Each pass runs three transactions per row: claim
 * (handled by {@link MailOutboxClaimer}), send (outside any transaction so the lease holds even
 * if the JVM crashes mid-call), resolve (handled by {@link MailOutboxResolver}).
 *
 * <p>The {@code FOR UPDATE SKIP LOCKED} on the claim query lets multiple workers run on the
 * same VM without competing. Multi-instance deployments need a distributed lock; today the
 * single VM is the lock.
 */
// HACK: in-process @Scheduled poller. Multi-instance breaks; idempotency saves us but doubles DB churn.
@Component
public class MailOutboxWorker {

    private static final Logger LOG = LoggerFactory.getLogger(MailOutboxWorker.class);

    private static final String IDEMPOTENCY_KEY_PREFIX = "outbox-";

    private final MailOutboxRepository repository;

    private final MailOutboxClaimer claimer;

    private final MailOutboxResolver resolver;

    private final MailSender mailSender;

    private final PasswordResetTemplate passwordResetTemplate;

    private final EmailVerificationTemplate emailVerificationTemplate;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public MailOutboxWorker(
        final MailOutboxRepository repository,
        final MailOutboxClaimer claimer,
        final MailOutboxResolver resolver,
        final MailSender mailSender,
        final PasswordResetTemplate passwordResetTemplate,
        final EmailVerificationTemplate emailVerificationTemplate
    ) {
        this.repository = repository;
        this.claimer = claimer;
        this.resolver = resolver;
        this.mailSender = mailSender;
        this.passwordResetTemplate = passwordResetTemplate;
        this.emailVerificationTemplate = emailVerificationTemplate;
    }

    /**
     * Drains one batch from the outbox. Called by {@link MailOutboxScheduler} on a fixed
     * interval in production; tests invoke directly for deterministic sequencing.
     */
    public void drain() {
        final List<Long> claimedIds = claimer.claimBatch();
        for (final Long claimedId : claimedIds) {
            processOne(claimedId);
        }
    }

    private void processOne(final long outboxId) {
        final MailOutbox row = repository.findById(outboxId).orElse(null);
        if (row == null) {
            LOG.warn("mail.outbox.process.row-vanished id={}", outboxId);
            return;
        }
        final MailMessage message = new MailMessage(
            row.getRecipient(),
            subjectFor(row.getTemplate()),
            renderBody(row),
            IDEMPOTENCY_KEY_PREFIX + outboxId
        );
        try {
            mailSender.send(message);
            resolver.markSent(outboxId);
        } catch (final MailSendException failure) {
            resolver.handleFailure(outboxId, row.getAttemptCount(), failure);
        }
    }

    private static String subjectFor(final String template) {
        if (MailOutboxService.TEMPLATE_PASSWORD_RESET.equals(template)) {
            return PasswordResetTemplate.SUBJECT;
        }
        if (MailOutboxService.TEMPLATE_EMAIL_VERIFICATION.equals(template)) {
            return EmailVerificationTemplate.SUBJECT;
        }
        throw unknownTemplate(template);
    }

    private String renderBody(final MailOutbox row) {
        if (MailOutboxService.TEMPLATE_PASSWORD_RESET.equals(row.getTemplate())) {
            return passwordResetTemplate.renderBody(row.getPayload());
        }
        if (MailOutboxService.TEMPLATE_EMAIL_VERIFICATION.equals(row.getTemplate())) {
            return emailVerificationTemplate.renderBody(row.getPayload());
        }
        throw unknownTemplate(row.getTemplate());
    }

    private static IllegalStateException unknownTemplate(final String template) {
        return new IllegalStateException("unknown mail template: " + template);
    }
}
