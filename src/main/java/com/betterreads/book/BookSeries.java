package com.betterreads.book;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.betterreads.booksource.SeriesEntry;

@Embeddable
record BookSeries(
    @Column(name = "series_name", nullable = false) String name,
    @Column(name = "position", nullable = false) int position
) {

    static BookSeries from(final SeriesEntry entry) {
        return new BookSeries(entry.name(), entry.position());
    }

    SeriesEntry toEntry() {
        return new SeriesEntry(name, position);
    }
}
