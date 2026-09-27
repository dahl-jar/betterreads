package com.betterreads.features.comments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.Nullable;

/** A user's comment on a book or a review, a reply when it has a parent comment. */
@Entity
@Table(name = "comment")
// NullAway.Init, PMD.DataClass: JPA sets the fields reflectively and an entity is a data holder.
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private CommentTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "parent_comment_id")
    @Nullable
    private Long parentCommentId;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Comment() {
    }

    public Comment(final Long userId, final CommentTarget targetType, final Long targetId,
        final @Nullable Long parentCommentId, final String body) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.parentCommentId = parentCommentId;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getCommentId() {
        return commentId;
    }

    public Long getUserId() {
        return userId;
    }

    public CommentTarget getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    @Nullable
    public Long getParentCommentId() {
        return parentCommentId;
    }

    public String getBody() {
        return body;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
