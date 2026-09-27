package com.betterreads.clients.mail;

/**
 * One outbound mail with a plaintext body.
 *
 * @param idempotencyKey same value on every retry of an outbox row, so the provider sends it once
 */
public record MailMessage(String recipient, String subject, String body, String idempotencyKey) { }
