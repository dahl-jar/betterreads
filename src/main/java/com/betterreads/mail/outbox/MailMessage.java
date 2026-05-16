package com.betterreads.mail.outbox;

/**
 * A single outbound mail.
 *
 * @param recipient destination address
 * @param subject subject line
 * @param body plaintext body
 * @param idempotencyKey stable per outbox row so provider-side dedupe collapses retries
 */
public record MailMessage(String recipient, String subject, String body, String idempotencyKey) { }
