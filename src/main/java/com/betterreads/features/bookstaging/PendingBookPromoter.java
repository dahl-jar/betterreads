package com.betterreads.features.bookstaging;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import com.betterreads.book.Book;
import com.betterreads.book.BookPromotedEvent;
import com.betterreads.book.BookUpsertService;
import com.betterreads.booksource.MergedBook;
import com.betterreads.features.bookstaging.RequiredFieldsCheck.MissingFields;
import com.betterreads.logging.LogSanitizer;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookMapper;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.pendingbook.PendingBookStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Promotes one collected candidate into {@code book}, or records what it is missing. */
@Component
class PendingBookPromoter {

    static final int MAX_ATTEMPTS = 30;

    private static final Logger LOG = LoggerFactory.getLogger(PendingBookPromoter.class);

    private final PendingBookRepository pendingBooks;

    private final BookUpsertService bookUpsertService;

    private final RequiredFieldsCheck requiredFields;

    private final PendingBookMapper mapper;

    private final ApplicationEventPublisher events;

    public PendingBookPromoter(
        final PendingBookRepository pendingBooks,
        final BookUpsertService bookUpsertService,
        final RequiredFieldsCheck requiredFields,
        final PendingBookMapper mapper,
        final ApplicationEventPublisher events
    ) {
        this.pendingBooks = pendingBooks;
        this.bookUpsertService = bookUpsertService;
        this.requiredFields = requiredFields;
        this.mapper = mapper;
        this.events = events;
    }

    /**
     * The failed promote rolled back its transaction and any attempt stamp with it, so the attempt is
     * written in a fresh one. An unstamped candidate re-enters every poll at full source cost.
     */
    @Transactional
    public void recordFailedAttempt(final String dedupKey) {
        update(dedupKey, PendingBookPromoter::recordAttempt);
    }

    @Transactional
    public void markDuplicate(final String dedupKey) {
        update(dedupKey, row -> row.setStatus(PendingBookStatus.DUPLICATE));
    }

    @Transactional
    public void promote(final String dedupKey, final MergedBook collected) {
        update(dedupKey, row -> {
            mapper.applyTo(row, collected);
            final MissingFields missing = requiredFields.check(collected.book());
            if (missing.isReady()) {
                final Book promoted = bookUpsertService.upsertFromSource(collected);
                row.setStatus(PendingBookStatus.PROMOTED);
                events.publishEvent(new BookPromotedEvent(promoted.getDedupKey()));
            } else {
                LOG.info("catalog.staging not promoted dedupKey={} missing={}",
                    LogSanitizer.forLog(dedupKey), LogSanitizer.forLog(String.join(",", missing.missing())));
                row.setMissingFields(mapper.join(missing.missing()));
                recordAttempt(row);
            }
        });
    }

    private void update(final String dedupKey, final Consumer<PendingBook> change) {
        pendingBooks.findByDedupKey(dedupKey).ifPresent(row -> {
            change.accept(row);
            pendingBooks.save(row);
        });
    }

    private static void recordAttempt(final PendingBook row) {
        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setLastAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (row.getAttemptCount() >= MAX_ATTEMPTS) {
            row.setStatus(PendingBookStatus.INCOMPLETE_FINAL);
        }
    }
}
