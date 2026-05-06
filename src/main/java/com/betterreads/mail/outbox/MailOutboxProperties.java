package com.betterreads.mail.outbox;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox-worker tunables bound from {@code mail.outbox.*}. Defaults are sized for personal-scale
 * traffic on a single VM: claim 10 rows per pass, retry up to 3 times total, hold a 5-minute
 * lease on each claimed row so a crashed worker's claims free up quickly.
 *
 * @param workerEnabled set to {@code false} in tests so rows stay readable for assertions
 * @param claimBatchSize rows pulled per scheduler tick
 * @param maxAttempts attempts per row before marking it permanently failed
 * @param leaseSeconds how far {@code next_attempt_at} bumps when a row is claimed
 */
@Validated
@ConfigurationProperties(prefix = "mail.outbox")
public record MailOutboxProperties(
    boolean workerEnabled,
    @Positive int claimBatchSize,
    @Positive int maxAttempts,
    @Positive int leaseSeconds
) { }
