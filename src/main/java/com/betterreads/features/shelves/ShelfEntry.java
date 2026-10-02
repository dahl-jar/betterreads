package com.betterreads.features.shelves;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.ZoneOffset;

import com.betterreads.db.Timestamped;
import org.jspecify.annotations.Nullable;

/** One book on one user's shelf. A user shelves a given book once. */
@Entity
@Table(name = "user_book_collection")
// NullAway.Init, PMD.DataClass: JPA sets the fields reflectively and an entity is a data holder.
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class ShelfEntry extends Timestamped {

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

    protected ShelfEntry() {
        super();
    }

    /** New entries start at WANT_TO_READ. */
    public ShelfEntry(final Long userId, final Long bookId) {
        super();
        this.userId = userId;
        this.bookId = bookId;
        this.status = ReadingStatus.WANT_TO_READ;
    }

    /**
     * Stamps today as the start or finish date when none is set, so a re-read keeps the first
     * pass's start date. Going back to reading clears the finish date, since an old finish date
     * would sit before the start and fail the date check on the next update.
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

    public Long getBookId() {
        return bookId;
    }

    public ReadingStatus getStatus() {
        return status;
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
