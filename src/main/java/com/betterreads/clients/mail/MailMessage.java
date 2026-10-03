package com.betterreads.clients.mail;

/**
 * One outbound mail with a plaintext version and an HTML version.
 *
 * @param idempotencyKey same value on every retry of an outbox row, so the provider sends it once
 */
public record MailMessage(String recipient, String subject, String text, String html, String idempotencyKey) { }
