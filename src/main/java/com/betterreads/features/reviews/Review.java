package com.betterreads.features.reviews;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.Nullable;

/** One user's review of one book, a 1-5 rating with optional title and body. */
@Entity
@Table(name = "review")
// NullAway.Init, PMD.DataClass: JPA sets the fields reflectively and an entity is a data holder.
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "rating")
    @Nullable
    private Integer rating;

    @Column(name = "title")
    @Nullable
    private String title;

    @Column(name = "body")
    @Nullable
    private String body;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Review() {
    }

    public Review(final Long userId, final Long bookId) {
        this.userId = userId;
        this.bookId = bookId;
    }

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

    public Long getReviewId() {
        return reviewId;
    }

    public Long getBookId() {
        return bookId;
    }

    @Nullable
    public Integer getRating() {
        return rating;
    }

    public void setRating(@Nullable final Integer rating) {
        this.rating = rating;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    public void setTitle(@Nullable final String title) {
        this.title = title;
    }

    @Nullable
    public String getBody() {
        return body;
    }

    public void setBody(@Nullable final String body) {
        this.body = body;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
