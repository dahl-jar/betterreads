package com.betterreads.mailoutbox;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Queues outbound email. The enqueue joins the caller's transaction, so the row commits or
 * rolls back with the state the email refers to.
 */
@Service
public class MailOutboxService {

    /** Must match the {@code mail_outbox.template} CHECK constraint. */
    public static final String TEMPLATE_PASSWORD_RESET = "password_reset";

    /** Must match the {@code mail_outbox.template} CHECK constraint. */
    public static final String TEMPLATE_EMAIL_VERIFICATION = "email_verification";

    private final MailOutboxRepository repository;

    private final ObjectMapper objectMapper;

    public MailOutboxService(final MailOutboxRepository repository, final ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueuePasswordReset(final String recipient, final String plaintextToken) {
        enqueue(TEMPLATE_PASSWORD_RESET, recipient, plaintextToken);
    }

    @Transactional
    public void enqueueEmailVerification(final String recipient, final String plaintextToken) {
        enqueue(TEMPLATE_EMAIL_VERIFICATION, recipient, plaintextToken);
    }

    private void enqueue(final String template, final String recipient, final String plaintextToken) {
        final MailOutbox row = new MailOutbox();
        row.setTemplate(template);
        row.setRecipient(recipient);
        row.setPayload(serialize(Map.of("token", plaintextToken)));
        final Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setNextAttemptAt(now);
        repository.save(row);
    }

    private String serialize(final Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (final JacksonException ex) {
            throw new IllegalStateException("failed to serialize outbox payload", ex);
        }
    }
}
