package com.betterreads.mail.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point for queuing outbound email.
 *
 * <p>The enqueue joins the surrounding transaction so the row commits or rolls back together
 * with the business state the email refers to.
 */
@Service
public class MailOutboxService {

    /** Template name for password-reset emails. Value must match the DB CHECK constraint. */
    public static final String TEMPLATE_PASSWORD_RESET = "password_reset";

    /** Template name for email-verification emails. Value must match the DB CHECK constraint. */
    public static final String TEMPLATE_EMAIL_VERIFICATION = "email_verification";

    private final MailOutboxRepository repository;

    private final ObjectMapper objectMapper;

    public MailOutboxService(final MailOutboxRepository repository, final ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Enqueues a password-reset email. */
    @Transactional
    public void enqueuePasswordReset(final String recipient, final String plaintextToken) {
        enqueue(TEMPLATE_PASSWORD_RESET, recipient, plaintextToken);
    }

    /** Enqueues an email-verification email. */
    @Transactional
    public void enqueueEmailVerification(final String recipient, final String plaintextToken) {
        enqueue(TEMPLATE_EMAIL_VERIFICATION, recipient, plaintextToken);
    }

    private void enqueue(final String template, final String recipient, final String plaintextToken) {
        final MailOutbox row = new MailOutbox();
        row.setTemplate(template);
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
