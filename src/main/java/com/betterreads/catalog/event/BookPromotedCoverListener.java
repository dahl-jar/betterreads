package com.betterreads.catalog.event;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookCoverRepository;
import com.betterreads.catalog.service.source.CoverMirrorService;
import com.betterreads.common.util.LogSanitizer;
import com.betterreads.integration.minio.ImageStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Mirrors a promoted book's cover into object storage after its promotion commits.
 *
 * <p>Runs on a dedicated executor because mirroring downloads the external cover and writes it to
 * MinIO, work too slow for the commit thread. A failure leaves the book on its external URL, which
 * the image endpoint still mirrors on first read, so a missed mirror self-heals.
 */
@Component
public class BookPromotedCoverListener {

    private static final Logger LOG = LoggerFactory.getLogger(BookPromotedCoverListener.class);

    private final BookCoverRepository books;

    private final CoverMirrorService coverMirror;

    private final Executor executor;

    public BookPromotedCoverListener(
        final BookCoverRepository books,
        final CoverMirrorService coverMirror,
        @Qualifier("coverMirrorExecutor") final Executor coverMirrorExecutor
    ) {
        this.books = books;
        this.coverMirror = coverMirror;
        this.executor = coverMirrorExecutor;
    }

    /** Mirrors the promoted book's cover off the commit thread. */
    @TransactionalEventListener
    public void onBookPromoted(final BookPromotedEvent event) {
        executor.execute(() -> mirror(event.dedupKey()));
    }

    private void mirror(final String dedupKey) {
        try {
            final Book book = books.findByDedupKey(dedupKey).orElse(null);
            if (book == null || book.getCoverUrl() == null) {
                return;
            }
            final OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
            coverMirror.mirror(dedupKey, book.getCoverUrl())
                .ifPresentOrElse(
                    key -> books.markCoverMirrored(book.getBookId(), key, checkedAt),
                    () -> books.markCoverChecked(book.getBookId(), checkedAt));
        } catch (WebClientException | DataAccessException | ImageStoreException ex) {
            LOG.warn("catalog.cover-mirror failed for promoted key={} ({}), leaving external url",
                LogSanitizer.forLog(dedupKey), ex.getClass().getSimpleName());
        }
    }
}
