package com.betterreads.mailoutbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * One queued outbound email. The row id is also the idempotency key sent to the provider, so a
 * retried row sends once.
 */
@Entity
@Table(name = "mail_outbox")
// NullAway.Init, PMD.DataClass: JPA sets the fields reflectively and an entity is a data holder.
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class MailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mail_outbox_id")
    private Long mailOutboxId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String template;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recipient;

    @Column(nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    @Nullable
    private Instant sentAt;

    @Column(name = "failed_at")
    @Nullable
    private Instant failedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    @Nullable
    private String lastError;

    public Long getMailOutboxId() {
        return mailOutboxId;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(final String template) {
        this.template = template;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(final String recipient) {
        this.recipient = recipient;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(final String payload) {
        this.payload = payload;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(@Nullable final Instant sentAt) {
        this.sentAt = sentAt;
    }

    @Nullable
    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(@Nullable final Instant failedAt) {
        this.failedAt = failedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(final int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(final Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    @Nullable
    public String getLastError() {
        return lastError;
    }

    public void setLastError(@Nullable final String lastError) {
        this.lastError = lastError;
    }
}
