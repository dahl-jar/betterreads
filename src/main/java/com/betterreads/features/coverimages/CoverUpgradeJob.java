package com.betterreads.features.coverimages;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.book.Book;
import com.betterreads.book.BookChangedEvent;
import com.betterreads.clients.itunes.ItunesUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class CoverUpgradeJob {

    private static final Logger LOG = LoggerFactory.getLogger(CoverUpgradeJob.class);

    private final BookCoverRepository books;

    private final CoverPicker picker;

    private final CoverMirrorService mirror;

    private final ApplicationEventPublisher events;

    private final TransactionTemplate transactions;

    private final CoverUpgradeProperties properties;

    // PMD.ExcessiveParameterList: six injected collaborators, the constructor is Spring's injection point.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    CoverUpgradeJob(
        final BookCoverRepository books,
        final CoverPicker picker,
        final CoverMirrorService mirror,
        final ApplicationEventPublisher events,
        final TransactionTemplate transactions,
        final CoverUpgradeProperties properties
    ) {
        this.books = books;
        this.picker = picker;
        this.mirror = mirror;
        this.events = events;
        this.transactions = transactions;
        this.properties = properties;
    }

    void upgrade() {
        final List<Book> candidates = books.findUpgradeCandidates(
            now().minus(properties.searchAgainAfter()), PageRequest.ofSize(properties.batchSize()));
        int checked = 0;
        int replaced = 0;
        int cleared = 0;
        for (final Book book : candidates) {
            final Outcome outcome = attempt(book);
            if (outcome == Outcome.STOPPED) {
                break;
            }
            replaced += outcome == Outcome.REPLACED ? 1 : 0;
            cleared += outcome == Outcome.CLEARED ? 1 : 0;
            checked++;
        }
        LOG.info("catalog.cover-upgrade checked={} replaced={} cleared={}", checked, replaced, cleared);
    }

    // Checkstyle.IllegalCatch + PMD.AvoidCatchingGenericException: one bad book must not stall the run
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private Outcome attempt(final Book book) {
        try {
            books.saveCoverSearchedAt(book.getBookId(), now());
            return upgradeOne(book);
        } catch (ItunesUnavailableException ex) {
            LOG.warn("catalog.cover-upgrade stopped, Apple lookup unavailable");
            restoreSearchTime(book);
            return Outcome.STOPPED;
        } catch (RuntimeException ex) {
            LOG.warn("catalog.cover-upgrade skipped bookId={} ({})", book.getBookId(), ex.getClass().getSimpleName());
            return Outcome.KEPT;
        }
    }

    private Outcome upgradeOne(final Book book) {
        return picker.best(book)
            .map(picked -> replace(book, picked))
            .orElseGet(() -> clearIfRejected(book));
    }

    private Outcome replace(final Book book, final PickedCover picked) {
        final CoverCandidate cover = picked.candidate();
        return mirror.mirror(book.getDedupKey(), cover.url(), picked.image())
            .map(objectKey -> {
                commit(book, () -> books.applyCover(book.getBookId(), cover, objectKey, now()));
                return cover.url().equals(book.getCoverUrl()) ? Outcome.KEPT : Outcome.REPLACED;
            })
            .orElseGet(() -> {
                restoreSearchTime(book);
                return Outcome.KEPT;
            });
    }

    private Outcome clearIfRejected(final Book book) {
        if (picker.rejectsCurrent(book)) {
            commit(book, () -> books.clearCover(book.getBookId(), now()));
            return Outcome.CLEARED;
        }
        return Outcome.KEPT;
    }

    private void restoreSearchTime(final Book book) {
        books.saveCoverSearchedAt(book.getBookId(), book.getCoverSearchedAt());
    }

    private void commit(final Book book, final Runnable write) {
        transactions.executeWithoutResult(status -> {
            write.run();
            events.publishEvent(new BookChangedEvent(book.getBookId()));
        });
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private enum Outcome { REPLACED, CLEARED, KEPT, STOPPED }
}
