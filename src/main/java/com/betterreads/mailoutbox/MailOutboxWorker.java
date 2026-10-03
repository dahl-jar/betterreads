package com.betterreads.mailoutbox;

import com.betterreads.clients.mail.MailMessage;
import com.betterreads.clients.mail.MailSendException;
import com.betterreads.clients.mail.MailSender;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends queued outbox mail. The send runs outside any transaction, so a crash mid-call leaves
 * the row claimed until its lease ends and another drain picks it up.
 */
@Component
class MailOutboxWorker {

    private static final Logger LOG = LoggerFactory.getLogger(MailOutboxWorker.class);

    private static final String IDEMPOTENCY_KEY_PREFIX = "outbox-";

    private final MailOutboxRepository repository;

    private final MailOutboxClaimer claimer;

    private final MailOutboxResolver resolver;

    private final MailSender mailSender;

    private final Map<String, MailTemplate> templates;

    MailOutboxWorker(
        final MailOutboxRepository repository,
        final MailOutboxClaimer claimer,
        final MailOutboxResolver resolver,
        final MailSender mailSender,
        final List<MailTemplate> templates
    ) {
        this.repository = repository;
        this.claimer = claimer;
        this.resolver = resolver;
        this.mailSender = mailSender;
        this.templates = templates.stream()
            .collect(Collectors.toUnmodifiableMap(MailTemplate::name, Function.identity()));
    }

    void drain() {
        claimer.claimBatch().forEach(this::sendOne);
    }

    private void sendOne(final long outboxId) {
        repository.findById(outboxId).ifPresentOrElse(
            row -> send(row, outboxId),
            () -> LOG.warn("Claimed outbox row vanished before send id={}", outboxId));
    }

    private void send(final MailOutbox row, final long outboxId) {
        final MailMessage message;
        try {
            final MailTemplate template = templateFor(row.getTemplate());
            final MailLayout.MailContent content = template.content(row.getPayload());
            message = new MailMessage(
                row.getRecipient(),
                template.subject(),
                MailLayout.text(content),
                MailLayout.html(content),
                IDEMPOTENCY_KEY_PREFIX + outboxId
            );
        } catch (final IllegalStateException failure) {
            resolver.recordRenderFailure(outboxId, failure);
            return;
        }
        try {
            mailSender.send(message);
            resolver.markSent(outboxId);
        } catch (final MailSendException failure) {
            resolver.recordFailure(outboxId, row.getAttemptCount(), failure);
        }
    }

    private MailTemplate templateFor(final String name) {
        return Optional.ofNullable(templates.get(name))
            .orElseThrow(() -> new IllegalStateException("unknown mail template: " + name));
    }
}
