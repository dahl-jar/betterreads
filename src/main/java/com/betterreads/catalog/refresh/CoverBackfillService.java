package com.betterreads.catalog.refresh;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookCoverRepository;
import com.betterreads.catalog.service.source.CoverMirrorService;
import com.betterreads.integration.minio.ImageStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Mirrors the covers of already-promoted books that the promotion-time mirror did not reach, a
 * bounded slice per run.
 *
 * <p>A run picks the least recently checked candidates and stamps each as checked, so successive runs
 * walk the catalog without re-doing books already tried. The object key is written with a targeted
 * update, leaving rating and community columns untouched. One failing book is logged and skipped.
 */
@Service
public class CoverBackfillService {

    private static final Logger LOG = LoggerFactory.getLogger(CoverBackfillService.class);

    private static final int SLICE_SIZE = 50;

    private final BookCoverRepository books;

    private final CoverMirrorService coverMirror;

    public CoverBackfillService(final BookCoverRepository books, final CoverMirrorService coverMirror) {
        this.books = books;
        this.coverMirror = coverMirror;
    }

    /** Mirrors covers for one slice of un-mirrored books. */
    public void backfillSlice() {
        final List<Book> candidates = books.findCoverBackfillCandidates(PageRequest.ofSize(SLICE_SIZE));
        LOG.info("catalog.cover-backfill mirroring books={}", candidates.size());
        candidates.forEach(this::mirrorOne);
    }

    /**
     * Mirrors every un-mirrored book's cover, a slice at a time until each has been visited once. A
     * book the sources cannot mirror keeps its null object key, so the sweep query excludes books
     * already checked since the run started, which both drops mirrored books and stops the run from
     * retrying the same failures forever.
     */
    public void fullSweep() {
        final OffsetDateTime runStart = OffsetDateTime.now(ZoneOffset.UTC);
        List<Book> slice = books.findCoverSweepCandidates(runStart, PageRequest.ofSize(SLICE_SIZE));
        while (!slice.isEmpty()) {
            LOG.info("catalog.cover-sweep books={}", slice.size());
            slice.forEach(this::mirrorOne);
            slice = books.findCoverSweepCandidates(runStart, PageRequest.ofSize(SLICE_SIZE));
        }
    }

    private void mirrorOne(final Book book) {
        final String coverUrl = book.getCoverUrl();
        if (coverUrl == null) {
            return;
        }
        final OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            coverMirror.mirror(book.getDedupKey(), coverUrl)
                .ifPresentOrElse(
                    key -> books.markCoverMirrored(book.getBookId(), key, checkedAt),
                    () -> books.markCoverChecked(book.getBookId(), checkedAt));
        } catch (WebClientException | DataAccessException | ImageStoreException ex) {
            LOG.warn("catalog.cover-backfill failed for one book ({}), skipping it",
                ex.getClass().getSimpleName());
        }
    }
}
