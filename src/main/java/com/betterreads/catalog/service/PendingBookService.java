package com.betterreads.catalog.service;

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
        final MergedBook collected = sourceCollector.collectFor(mapper.toSourceBook(row));
        promoter.promote(dedupKey, collected);
    }
}
