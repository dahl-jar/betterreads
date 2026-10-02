package com.betterreads.book;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.booksource.SeriesEntry;
import org.springframework.stereotype.Component;

@Component
class SeriesChangeRecorder {

    private final BookSeriesChangeRepository changes;

    SeriesChangeRecorder(final BookSeriesChangeRepository changes) {
        this.changes = changes;
    }

    void recordAndRequestCheck(final Book book, final List<SeriesEntry> seriesBefore) {
        final List<SeriesEntry> seriesAfter = book.getSeries();
        if (book.getBookId() == null || seriesBefore.equals(seriesAfter)) {
            return;
        }
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        changes.save(new BookSeriesChange(book.getBookId(), now, seriesBefore, seriesAfter));
        if (book.getMetadataCheckRequestedAt() == null) {
            book.setMetadataCheckRequestedAt(now);
        }
    }
}
