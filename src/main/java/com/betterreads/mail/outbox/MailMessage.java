package com.betterreads.mail.outbox;

/**
 * Carrier for a single outbound mail. {@code idempotencyKey} should be stable per outbox row
 * (not per attempt) so a transport-side dedupe collapses retries of the same row.
 *
 * @param recipient destination address, already lowercased
 * @param subject mail subject line
 * @param body plaintext body
 * @param idempotencyKey provider-side dedupe key, stable per outbox row
 */
public record MailMessage(String recipient, String subject, String body, String idempotencyKey) { }
