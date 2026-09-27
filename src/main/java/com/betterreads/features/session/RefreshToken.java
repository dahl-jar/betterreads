package com.betterreads.features.session;

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
 * Refresh token row, looked up by its HMAC-SHA256 {@code tokenHash}.
 * {@code replacedBy} points at the successor so a replayed old token can be spotted.
 */
@Entity
@Table(name = "refresh_token")
// NullAway.Init, PMD.DataClass: JPA sets the fields reflectively and an entity is a data holder.
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "TEXT")
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    @Nullable
    private Instant revokedAt;

    @Column(name = "replaced_by")
    @Nullable
    private Long replacedBy;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }

    public Long getRefreshTokenId() {
        return refreshTokenId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    public void setTokenHash(final String tokenHash) {
        this.tokenHash = tokenHash;
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
    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(@Nullable final Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    @Nullable
    public Long getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(@Nullable final Long replacedBy) {
        this.replacedBy = replacedBy;
    }
}
