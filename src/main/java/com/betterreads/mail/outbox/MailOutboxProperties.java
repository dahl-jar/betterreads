package com.betterreads.mail.outbox;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox-worker tunables bound from {@code mail.outbox.*}.
 *
 * @param workerEnabled whether the worker drains the outbox on schedule
 * @param claimBatchSize rows pulled per scheduler tick
 * @param maxAttempts attempts per row before it is marked permanently failed
 * @param leaseSeconds seconds before a claimed row becomes eligible again if the worker crashes
 */
@Validated
@ConfigurationProperties(prefix = "mail.outbox")
public record MailOutboxProperties(
    boolean workerEnabled,
    @Positive int claimBatchSize,
    @Positive int maxAttempts,
    @Positive int leaseSeconds
) { }
