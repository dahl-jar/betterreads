package com.betterreads.catalog.service.pipeline;

import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SourceBook;

import java.util.List;

import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.mapper.PendingBookMapper;
import com.betterreads.catalog.repository.PendingBookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds books that are not yet complete in {@code pending_book} and promotes them into {@code book}
 * once they carry every required field.
 *
 * <p>Staging reserves a row by the book's dedup key, then fills in the merged view, so two stages of
 * the same book collapse to one row without a duplicate-key error. Promotion collects the remaining
 * sources for each candidate, re-merges, and moves the complete ones into the catalog. The source
 * fetches run outside any transaction; each candidate's write is its own short transaction in
 * {@link PendingBookPromoter}.
 */
@Service
public class PendingBookService {

    private static final String STATUS_PENDING = "PENDING";

    private final PendingBookRepository pendingBooks;

    private final PendingBookMapper mapper;

    private final SourceCollector sourceCollector;

    private final PendingBookPromoter promoter;

    public PendingBookService(
        final PendingBookRepository pendingBooks,
        final PendingBookMapper mapper,
        final SourceCollector sourceCollector,
        final PendingBookPromoter promoter
    ) {
        this.pendingBooks = pendingBooks;
        this.mapper = mapper;
        this.sourceCollector = sourceCollector;
        this.promoter = promoter;
    }

    /**
     * Stages the merged book, updating the existing row when the book is already known.
     *
     * @throws IllegalArgumentException if the merged book carries no source identifier to key on
     */
    @Transactional
    public void stage(final MergedBook merged) {
        final String dedupKey = merged.book().dedupKey();
        if (dedupKey == null) {
            throw new IllegalArgumentException(
                "merged book has no source identifier to stage on; nothing can dedup it");
        }
        pendingBooks.reserve(dedupKey);
        final PendingBook row = pendingBooks.findByDedupKey(dedupKey).orElseThrow(
            () -> new IllegalStateException("reserved pending row vanished for dedupKey=" + dedupKey));
        mapper.applyTo(row, merged);
        pendingBooks.save(row);
    }

    /**
     * Promotes an already-collected candidate at once, ahead of the next scheduled poll.
     *
     * <p>The merge is the collected, enriched view, so promotion reuses it without re-fetching the
     * sources. The promote runs in its own transaction in {@link PendingBookPromoter}, after the
     * staging transaction has committed, so its after-commit event sees the row.
     */
    public void promoteNow(final String dedupKey, final MergedBook merged) {
        promoter.promote(dedupKey, merged);
    }

    /**
     * Collects the remaining sources for every staged candidate and promotes the complete ones.
     *
     * <p>Not transactional: the per-candidate source fetches are slow external calls, so each write
     * runs in its own transaction in {@link PendingBookPromoter} rather than holding one connection
     * open across all of them.
     */
    public void promoteReady() {
        for (final String dedupKey : pendingKeys()) {
            collectAndPromote(dedupKey);
        }
    }

    private List<String> pendingKeys() {
        return pendingBooks.findByStatusOrderByFirstSeenAtAsc(STATUS_PENDING).stream()
            .map(PendingBook::getDedupKey)
            .toList();
    }

    private void collectAndPromote(final String dedupKey) {
        final PendingBook row = pendingBooks.findByDedupKey(dedupKey).orElse(null);
        if (row == null) {
            return;
        }
        final SourceBook staged = mapper.toSourceBook(row);
        final MergedBook collected = sourceCollector.collectFor(staged);
        promoter.promote(dedupKey, keepStagedSignals(collected, staged));
    }

    /**
     * Restores the rating and series the candidate was staged with when the collect re-merge dropped
     * them. Promotion rebuilds the staged row as a single OpenLibrary-tagged book, but the merger
     * resolves rating and series only from Hardcover and Wikidata, so those staged signals would
     * otherwise be lost on every promotion.
     */
    private static MergedBook keepStagedSignals(final MergedBook collected, final SourceBook staged) {
        final SourceBook book = collected.book();
        final SourceBook restored = book.toBuilder()
            .averageRating(book.averageRating() == null ? staged.averageRating() : book.averageRating())
            .ratingCount(book.ratingCount() == null ? staged.ratingCount() : book.ratingCount())
            .seriesName(book.seriesName() == null ? staged.seriesName() : book.seriesName())
            .seriesPosition(
                book.seriesPosition() == null ? staged.seriesPosition() : book.seriesPosition())
            .build();
        return collected.withBook(restored);
    }
}
