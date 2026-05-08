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

import org.jspecify.annotations.Nullable;

/**
 * Maps to {@code app_user} (Flyway V1). Both {@code username} and {@code email} are unique.
 * Timestamps are set by JPA lifecycle hooks so the application owns the clock.
 */
@Entity
@Table(name = "app_user")
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
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
}
