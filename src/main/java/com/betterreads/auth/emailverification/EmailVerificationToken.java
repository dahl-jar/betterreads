package com.betterreads.auth.emailverification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Maps to {@code email_verification_token} (Flyway V15). Stored as a keyed HMAC of the plaintext;
 * the plaintext exists only in the verification link sent to the user. Single-use via
 * {@code consumed_at}, plus a partial unique index on {@code (user_id) WHERE consumed_at IS NULL}
 * so each user has at most one outstanding token.
 */
@Entity
@Table(name = "email_verification_token")
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_verification_token_id")
    private Long emailVerificationTokenId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "TEXT")
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    @Nullable
    private Instant consumedAt;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }

    public Long getEmailVerificationTokenId() {
        return emailVerificationTokenId;
    }

    public void setEmailVerificationTokenId(final Long emailVerificationTokenId) {
        this.emailVerificationTokenId = emailVerificationTokenId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(final String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(final Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(final Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Nullable
    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(@Nullable final Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}
