package com.betterreads.features.coverimages;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.book.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Mirrors the covers the promotion-time mirror missed.
 *
 * <p>Every book tried is stamped as checked, so the next run starts with books not tried yet.
 */
@Service
class CoverBackfillService {

    private static final Logger LOG = LoggerFactory.getLogger(CoverBackfillService.class);

    private static final int SLICE_SIZE = 50;

    private static final Pageable SLICE_PAGE = PageRequest.ofSize(SLICE_SIZE);

    private final BookCoverRepository books;

    private final CoverMirrorService coverMirror;

    CoverBackfillService(final BookCoverRepository books, final CoverMirrorService coverMirror) {
        this.books = books;
        this.coverMirror = coverMirror;
    }

    public void backfillSlice() {
        final List<Book> candidates =
            books.findCoverSweepCandidates(OffsetDateTime.now(ZoneOffset.UTC), SLICE_PAGE);
        LOG.info("catalog.cover-backfill mirroring books={}", candidates.size());
        candidates.forEach(this::mirrorOne);
    }

    /** Mirrors every un-mirrored book a slice at a time, trying each book once per run. */
    public void fullSweep() {
        final OffsetDateTime runStart = OffsetDateTime.now(ZoneOffset.UTC);
        List<Book> slice = books.findCoverSweepCandidates(runStart, SLICE_PAGE);
        while (!slice.isEmpty()) {
            LOG.info("catalog.cover-sweep mirroring books={}", slice.size());
            slice.forEach(this::mirrorOne);
            slice = books.findCoverSweepCandidates(runStart, SLICE_PAGE);
        }
    }

    void mirrorOne(final Book book) {
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
        } catch (DataAccessException ex) {
            LOG.warn("catalog.cover-mirror failed for bookId={} ({}), skipping it",
                book.getBookId(), ex.getClass().getSimpleName());
        }
    }
}
