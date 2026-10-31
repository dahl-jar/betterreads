package com.betterreads.mailoutbox;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class MailOutboxService {

    public static final String TEMPLATE_PASSWORD_RESET = "password_reset";

    public static final String TEMPLATE_EMAIL_VERIFICATION = "email_verification";

    public static final String TEMPLATE_SIGNUP_ATTEMPT = "signup_attempt";

    private static final String PAYLOAD_TOKEN = "token";

    private final MailOutboxRepository repository;

    private final ObjectMapper objectMapper;

    public MailOutboxService(final MailOutboxRepository repository, final ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueuePasswordReset(final String recipient, final String plaintextToken) {
        enqueue(TEMPLATE_PASSWORD_RESET, recipient, Map.of(PAYLOAD_TOKEN, plaintextToken));
    }

    @Transactional
    public void enqueueEmailVerification(final String recipient, final String plaintextToken) {
        enqueue(TEMPLATE_EMAIL_VERIFICATION, recipient, Map.of(PAYLOAD_TOKEN, plaintextToken));
    }

    @Transactional
    public void enqueueSignupAttempt(final String recipient) {
        enqueue(TEMPLATE_SIGNUP_ATTEMPT, recipient, Map.of());
    }

    private void enqueue(final String template, final String recipient, final Map<String, Object> payload) {
        final MailOutbox row = new MailOutbox();
        row.setTemplate(template);
        row.setRecipient(recipient);
        row.setPayload(serialize(payload));
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
