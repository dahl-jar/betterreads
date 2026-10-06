package com.betterreads.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "book_metadata_change")
// NullAway.Init: JPA sets the fields reflectively.
@SuppressWarnings("NullAway.Init")
class BookMetadataChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "change_id")
    private Long changeId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "field", nullable = false)
    private String field;

    @Column(name = "old_value")
    @Nullable
    private String oldValue;

    @Column(name = "new_value")
    @Nullable
    private String newValue;

    @Column(name = "source_url")
    @Nullable
    private String sourceUrl;

    @Column(name = "quote")
    @Nullable
    private String quote;

    @Column(name = "check_version", nullable = false)
    private int checkVersion;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    protected BookMetadataChange() {
    }

    BookMetadataChange(final long bookId, final Change change, final int checkVersion, final OffsetDateTime changedAt) {
        final Optional<FieldEvidence> evidence = Optional.ofNullable(change.evidence());
        this.bookId = bookId;
        this.field = change.field().name();
        this.oldValue = change.oldValue();
        this.newValue = change.newValue();
        this.sourceUrl = evidence.map(FieldEvidence::sourceUrl).orElse(null);
        this.quote = evidence.map(FieldEvidence::quote).orElse(null);
        this.checkVersion = checkVersion;
        this.changedAt = changedAt;
    }

    record Change(
        VerifiedField field,
        @Nullable String oldValue,
        @Nullable String newValue,
        @Nullable FieldEvidence evidence
    ) {
    }
}
