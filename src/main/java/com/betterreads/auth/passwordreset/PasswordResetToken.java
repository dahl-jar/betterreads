package com.betterreads.auth.passwordreset;

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
 * Maps to {@code password_reset_token} (Flyway V12). Stored as a keyed HMAC of the plaintext;
 * the plaintext exists only in the email link sent to the user. Single-use: once
 * {@code consumedAt} is set the row cannot be redeemed again.
 */
@Entity
@Table(name = "password_reset_token")
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "password_reset_token_id")
    private Long passwordResetTokenId;

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

    public Long getPasswordResetTokenId() {
        return passwordResetTokenId;
    }

    public void setPasswordResetTokenId(final Long passwordResetTokenId) {
        this.passwordResetTokenId = passwordResetTokenId;
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
