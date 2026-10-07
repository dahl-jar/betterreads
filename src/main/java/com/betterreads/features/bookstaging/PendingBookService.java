package com.betterreads.features.bookstaging;

import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import com.betterreads.logging.LogSanitizer;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookMapper;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.pendingbook.PendingBookStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Holds incomplete books in {@code pending_book} and promotes them into {@code book} once complete. */
@Service
public class PendingBookService {

    private static final Logger LOG = LoggerFactory.getLogger(PendingBookService.class);

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
     * Reserves the row by dedup key before filling it, so two concurrent stages of the same book end
     * on one row with no duplicate-key error.
     */
    @Transactional
    public void stage(final MergedBook merged) {
        final String dedupKey = merged.book().dedupKey();
        if (dedupKey == null) {
            throw new IllegalArgumentException(
                "merged book has no source identifier to stage on, so nothing can dedup it");
        }
        pendingBooks.reserve(dedupKey);
        final PendingBook row = pendingBooks.findByDedupKey(dedupKey).orElseThrow(
            () -> new IllegalStateException("reserved pending row vanished for dedupKey=" + dedupKey));
        mapper.applyTo(row, merged);
        reviveIfRetired(row);
        pendingBooks.save(row);
    }

    private static void reviveIfRetired(final PendingBook row) {
        if (PendingBookStatus.INCOMPLETE_FINAL.equals(row.getStatus())) {
            row.setStatus(PendingBookStatus.PENDING);
            row.setAttemptCount(0);
        }
    }

    /**
     * Source fetches are slow external calls, so each candidate's write gets its own transaction and
     * no connection stays open across them.
     */
    public void promoteReady() {
        pendingKeys().forEach(this::collectAndPromote);
    }

    private List<String> pendingKeys() {
        return pendingBooks.findDue(OffsetDateTime.now(ZoneOffset.UTC)).stream()
            .map(PendingBook::getDedupKey)
            .toList();
    }

    /**
     * An integrity violation means the candidate resolves a source id another row already owns, which
     * no retry changes, so it is retired as a duplicate. Any other failure counts as an attempt and
     * retries after the backoff.
     */
    // Checkstyle.IllegalCatch + PMD.AvoidCatchingGenericException: one candidate must not stop the poll
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private void collectAndPromote(final String dedupKey) {
        pendingBooks.findByDedupKey(dedupKey).ifPresent(row -> {
            try {
                final SourceBook staged = mapper.toSourceBook(row);
                final MergedBook collected = sourceCollector.collectFor(staged);
                promoter.promote(dedupKey, keepStagedRating(collected, staged));
            } catch (DataIntegrityViolationException collision) {
                LOG.warn("catalog.staging candidate duplicates an existing row, retired dedupKey={}",
                    LogSanitizer.forLog(dedupKey));
                promoter.markDuplicate(dedupKey);
            } catch (RuntimeException ex) {
                LOG.warn("catalog.staging promotion failed, candidate retries after backoff dedupKey={} ({})",
                    LogSanitizer.forLog(dedupKey), ex.getClass().getSimpleName());
                promoter.recordFailedAttempt(dedupKey);
            }
        });
    }

    /**
     * The merger takes the rating only from Hardcover, so a re-promotion with no Hardcover match would
     * lose it.
     */
    private static MergedBook keepStagedRating(final MergedBook collected, final SourceBook staged) {
        final SourceBook book = collected.book();
        final SourceBook restored = book.toBuilder()
            .averageRating(book.averageRating() == null ? staged.averageRating() : book.averageRating())
            .ratingCount(book.ratingCount() == null ? staged.ratingCount() : book.ratingCount())
            .build();
        return collected.withBook(restored);
    }
}
