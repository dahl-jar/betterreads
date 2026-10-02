package com.betterreads.db;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.time.Instant;

@MappedSuperclass
// NullAway.Init, PMD.DataClass: JPA sets the fields reflectively and an entity is a data holder.
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class HashedToken {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "TEXT")
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    void stampIssuedAt() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
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
}
