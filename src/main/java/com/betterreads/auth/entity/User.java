package com.betterreads.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.hibernate.annotations.SQLRestriction;
import org.jspecify.annotations.Nullable;

/**
 * Maps to {@code app_user} (Flyway V1). Both {@code username} and {@code email} are unique.
 * Timestamps are set by JPA lifecycle hooks so the application owns the clock.
 *
 * <p>Soft-delete: {@link #deletedAt} is the deletion timestamp. The class-level
 * {@link SQLRestriction} hides every row with {@code deleted_at IS NOT NULL} from every
 * Hibernate-driven SELECT (derived finders, custom JPQL, inherited {@code findById},
 * {@code existsBy*}). Forgetting to gate a new finder is therefore the safe default; the auth
 * path stops surfacing deleted users automatically.
 *
 * <p>The hard-delete sweep needs to <em>see</em> deleted rows. It uses a native SQL
 * {@code DELETE FROM app_user} statement so the filter does not apply.
 */
@Entity
@Table(name = "app_user")
@SQLRestriction("deleted_at IS NULL")
@SuppressWarnings({"NullAway.Init", "PMD.DataClass", "PMD.ExcessivePublicCount"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    @Nullable
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    @Nullable
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String bio;

    @Column(name = "email_verified_at")
    @Nullable
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@Nullable final String displayName) {
        this.displayName = displayName;
    }

    @Nullable
    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(@Nullable final String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Nullable
    public String getBio() {
        return bio;
    }

    public void setBio(@Nullable final String bio) {
        this.bio = bio;
    }

    @Nullable
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(@Nullable final Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Nullable
    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(@Nullable final Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
