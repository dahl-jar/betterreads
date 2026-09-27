package com.betterreads.clients.mail;

@FunctionalInterface
public interface MailSender {

    /**
     * Throws {@code MailSendException} on a non-2xx response or transport failure. The
     * idempotency key goes to the provider when it supports one, so a retried row sends once.
     */
    void send(MailMessage message);
}
