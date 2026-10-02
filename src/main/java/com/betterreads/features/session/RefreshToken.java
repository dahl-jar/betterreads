package com.betterreads.features.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import com.betterreads.db.HashedToken;
import org.jspecify.annotations.Nullable;

/**
 * Refresh token row, looked up by its HMAC-SHA256 {@code tokenHash}.
 * {@code replacedBy} points at the successor so a replayed old token can be spotted.
 */
@Entity
@Table(name = "refresh_token")
// NullAway.Init, PMD.DataClass: JPA sets the fields reflectively and an entity is a data holder.
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class RefreshToken extends HashedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @Column(name = "revoked_at")
    @Nullable
    private Instant revokedAt;

    @Column(name = "replaced_by")
    @Nullable
    private Long replacedBy;

    @Column(name = "persistent", nullable = false)
    private boolean persistent;

    public Long getRefreshTokenId() {
        return refreshTokenId;
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

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(final boolean persistent) {
        this.persistent = persistent;
    }
}
