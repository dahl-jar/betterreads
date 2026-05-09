package com.betterreads.auth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Maps to {@code email_token} (Flyway V16). One row per single-use token sent over email.
 * Stored as a keyed HMAC of the plaintext; the plaintext exists only in the email link sent
 * to the user. Single-use via {@code consumed_at}, plus a partial unique index per purpose on
 * {@code (user_id) WHERE consumed_at IS NULL AND purpose = ?} so each user can hold at most
 * one active token per purpose.
 *
 * <p>Replaces the prior per-domain {@code password_reset_token} and {@code email_verification_token}
 * tables. Storage is unified, behavior is not: each calling service ({@code PasswordResetService},
 * {@code EmailVerificationService}) keeps its own lifecycle and consume rules.
 */
@Entity
@Table(name = "email_token")
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class EmailToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_token_id")
    private Long emailTokenId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "purpose", nullable = false, columnDefinition = "TEXT")
    @Enumerated(EnumType.STRING)
    private Purpose purpose;

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

    public Long getEmailTokenId() {
        return emailTokenId;
    }

    public void setEmailTokenId(final Long emailTokenId) {
        this.emailTokenId = emailTokenId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public void setPurpose(final Purpose purpose) {
        this.purpose = purpose;
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

    /**
     * Discriminator for which feature owns a row. Mirrors the {@code chk_email_token_purpose}
     * CHECK constraint; renaming a value requires a coordinated Flyway migration.
     */
    public enum Purpose {
        PASSWORD_RESET,
        EMAIL_VERIFICATION
    }
}
