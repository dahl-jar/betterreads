package com.betterreads.mail.outbox;

/**
 * Sends a single outbound email. Implementations route to a transport (Resend, logging stub)
 * and surface only success or {@link MailSendException} so the worker's resolve logic stays
 * uniform.
 */
@FunctionalInterface
public interface MailSender {

    /**
     * Sends a single email. Throws {@link MailSendException} on any non-2xx response or
     * transport-level failure. The {@code message.idempotencyKey()} is forwarded to the
     * provider when supported so retries of the same outbox row never duplicate.
     */
    void send(MailMessage message);
}
