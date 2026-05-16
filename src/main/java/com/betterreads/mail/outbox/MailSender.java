package com.betterreads.mail.outbox;

/** Sends a single outbound email. */
@FunctionalInterface
public interface MailSender {

    /**
     * Sends one email. Throws {@link MailSendException} on a non-2xx response or transport
     * failure. {@code idempotencyKey} is forwarded to the provider when supported so retries
     * of the same outbox row dedupe.
     */
    void send(MailMessage message);
}
