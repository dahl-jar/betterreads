package com.betterreads.features.account;

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
 * One row per single-use token sent over email.
 *
 * <p>Only the hash is stored. The plaintext lives only in the email link. A partial unique
 * index limits each user to one active token per purpose.
 */
@Entity
@Table(name = "email_token")
// NullAway.Init, PMD.DataClass: JPA sets the fields reflectively and an entity is a data holder.
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
        issuedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    public void setPurpose(final Purpose purpose) {
        this.purpose = purpose;
    }

    public void setTokenHash(final String tokenHash) {
        this.tokenHash = tokenHash;
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

    /** Which feature a token belongs to. A DB CHECK constraint lists the same names. */
    public enum Purpose {
        PASSWORD_RESET,
        EMAIL_VERIFICATION
    }
}
