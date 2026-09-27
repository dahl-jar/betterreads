package com.betterreads.features.coverimages;

import java.util.concurrent.Executor;

import com.betterreads.book.BookPromotedEvent;
import com.betterreads.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mirrors a promoted book's cover after the promotion commits.
 *
 * <p>Downloading and storing the cover is too slow for the commit thread, so it runs on its own
 * executor. A failed mirror leaves the external URL, and the image endpoint mirrors it on first read.
 */
@Component
class BookPromotedCoverListener {

    private static final Logger LOG = LoggerFactory.getLogger(BookPromotedCoverListener.class);

    private final BookCoverRepository books;

    private final CoverBackfillService backfill;

    private final Executor executor;

    BookPromotedCoverListener(
        final BookCoverRepository books,
        final CoverBackfillService backfill,
        @Qualifier("coverMirrorExecutor") final Executor coverMirrorExecutor
    ) {
        this.books = books;
        this.backfill = backfill;
        this.executor = coverMirrorExecutor;
    }

    @TransactionalEventListener
    public void onBookPromoted(final BookPromotedEvent event) {
        executor.execute(() -> mirror(event.dedupKey()));
    }

    private void mirror(final String dedupKey) {
        try {
            books.findByDedupKey(dedupKey).ifPresent(backfill::mirrorOne);
        } catch (DataAccessException ex) {
            LOG.warn("catalog.cover-mirror failed for promoted key={} ({}), leaving external url",
                LogSanitizer.forLog(dedupKey), ex.getClass().getSimpleName());
        }
    }
}
