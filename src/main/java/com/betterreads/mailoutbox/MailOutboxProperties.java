package com.betterreads.mailoutbox;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param claimBatchSize rows claimed per scheduler tick
 * @param maxAttempts attempts per row before it is marked failed
 * @param leaseSeconds seconds before a claimed row is claimable again if the worker crashes
 */
@Validated
@ConfigurationProperties(prefix = "mail.outbox")
record MailOutboxProperties(
    @Positive int claimBatchSize,
    @Positive int maxAttempts,
    @Positive int leaseSeconds
) { }
