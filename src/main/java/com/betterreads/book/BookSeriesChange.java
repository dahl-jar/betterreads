package com.betterreads.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.betterreads.booksource.SeriesEntry;

@Entity
@Table(name = "book_series_change")
// NullAway.Init: JPA sets the fields reflectively.
@SuppressWarnings("NullAway.Init")
class BookSeriesChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "change_id")
    private Long changeId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "old_series", nullable = false)
    private String oldSeries;

    @Column(name = "new_series", nullable = false)
    private String newSeries;

    protected BookSeriesChange() {
    }

    BookSeriesChange(
        final Long bookId,
        final OffsetDateTime changedAt,
        final List<SeriesEntry> oldSeries,
        final List<SeriesEntry> newSeries
    ) {
        this.bookId = bookId;
        this.changedAt = changedAt;
        this.oldSeries = text(oldSeries);
        this.newSeries = text(newSeries);
    }

    private static String text(final List<SeriesEntry> series) {
        return series.stream()
            .map(entry -> entry.name() + " #" + entry.position())
            .collect(Collectors.joining(", "));
    }
}
