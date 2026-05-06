package com.betterreads.mail.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain entry point for queuing outbound email. Callers run inside their own transaction so
 * the enqueue commits or rolls back atomically with whatever business state the email refers
 * to (e.g. the password-reset token row).
 */
@Service
public class MailOutboxService {

    /**
     * Template name for password-reset emails. Kept as a constant so callers and the renderer
     * agree on the exact string the {@code chk_mail_outbox_template} CHECK constraint allows.
     */
    public static final String TEMPLATE_PASSWORD_RESET = "password_reset";

    private final MailOutboxRepository repository;

    private final ObjectMapper objectMapper;

    public MailOutboxService(final MailOutboxRepository repository, final ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueues a password-reset email. The plaintext token is stored in the JSON payload; the
     * worker reads it back to compose the link. The row is immediately eligible for the next
     * worker tick.
     */
    @Transactional
    public void enqueuePasswordReset(final String recipient, final String plaintextToken) {
        final MailOutbox row = new MailOutbox();
        row.setTemplate(TEMPLATE_PASSWORD_RESET);
        row.setRecipient(recipient);
        row.setPayload(serialize(Map.of("token", plaintextToken)));
        row.setCreatedAt(Instant.now());
        row.setNextAttemptAt(Instant.now());
        repository.save(row);
    }

    private String serialize(final Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (final JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize outbox payload", ex);
        }
    }
}
