package com.betterreads.collections.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.Nullable;

/**
 * Maps to {@code user_book_collection}: one row per user and book, holding the shelf status, the
 * favorite flag, optional reading dates, and a note. The {@code (user_id, book_id)} pair is unique,
 * so a user shelves a given book once.
 *
 * <p>Status changes stamp the reading dates through {@link #moveTo}: entering
 * {@link ReadingStatus#CURRENTLY_READING} fills {@code startedAt} the first time, marking
 * {@link ReadingStatus#FINISHED} fills {@code finishedAt} the first time. A re-read keeps the
 * original dates rather than overwriting them with today.
 */
@Entity
@Table(name = "user_book_collection")
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class ShelfEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collection_id")
    private Long collectionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "status", nullable = false)
    private ReadingStatus status;

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @Column(name = "started_at")
    @Nullable
    private LocalDate startedAt;

    @Column(name = "finished_at")
    @Nullable
    private LocalDate finishedAt;

    @Column(name = "notes")
    @Nullable
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ShelfEntry() {
    }

    /** Opens a shelf row for the given user and book at {@link ReadingStatus#WANT_TO_READ}. */
    public ShelfEntry(final Long userId, final Long bookId) {
        this.userId = userId;
        this.bookId = bookId;
        this.status = ReadingStatus.WANT_TO_READ;
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

    /**
     * Moves the entry to {@code target}, stamping the matching reading date on first entry.
     *
     * <p>An already-set {@code startedAt} or {@code finishedAt} is left as-is so a re-read keeps the
     * dates from the first pass. Moving to {@link ReadingStatus#CURRENTLY_READING} clears
     * {@code finishedAt}: the book is being read again, so a finish date left in place would sit
     * before the new start date and put the row in a state {@code updateEntry} rejects.
     */
    // PMD.NullAssignment: clearing finishedAt is the intended state, the book is no longer finished.
    @SuppressWarnings("PMD.NullAssignment")
    public void moveTo(final ReadingStatus target) {
        this.status = target;
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (target == ReadingStatus.CURRENTLY_READING) {
            this.finishedAt = null;
            if (this.startedAt == null) {
                this.startedAt = today;
            }
        }
        if (target == ReadingStatus.FINISHED && this.finishedAt == null) {
            this.finishedAt = today;
        }
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBookId() {
        return bookId;
    }

    public ReadingStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(final boolean favorite) {
        this.favorite = favorite;
    }

    @Nullable
    public LocalDate getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(@Nullable final LocalDate startedAt) {
        this.startedAt = startedAt;
    }

    @Nullable
    public LocalDate getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(@Nullable final LocalDate finishedAt) {
        this.finishedAt = finishedAt;
    }

    @Nullable
    public String getNotes() {
        return notes;
    }

    public void setNotes(@Nullable final String notes) {
        this.notes = notes;
    }
}
