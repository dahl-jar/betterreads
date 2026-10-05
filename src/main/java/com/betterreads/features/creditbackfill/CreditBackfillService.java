package com.betterreads.features.creditbackfill;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookUpsertService;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcoverbook.HardcoverClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
class CreditBackfillService {

    private static final Logger LOG = LoggerFactory.getLogger(CreditBackfillService.class);

    private final CreditBackfillRepository books;

    private final HardcoverClient hardcover;

    private final BookUpsertService upserts;

    private final AuthorRepository authors;

    private final CreditBackfillProperties properties;

    CreditBackfillService(
        final CreditBackfillRepository books,
        final HardcoverClient hardcover,
        final BookUpsertService upserts,
        final AuthorRepository authors,
        final CreditBackfillProperties properties
    ) {
        this.books = books;
        this.hardcover = hardcover;
        this.upserts = upserts;
        this.authors = authors;
        this.properties = properties;
    }

    public void backfillSlice() {
        final List<Book> candidates = books.findUncheckedCredits(PageRequest.ofSize(properties.sliceSize()));
        LOG.info("catalog.credit-backfill resolving books={}", candidates.size());
        final boolean rateLimited = candidates.stream().anyMatch(this::rateLimitedOn);
        if (rateLimited) {
            LOG.warn("catalog.credit-backfill rate limited by Hardcover, stopping until the next run");
        }
        authors.deleteUncredited();
    }

    private boolean rateLimitedOn(final Book book) {
        final String hardcoverId = Objects.requireNonNull(book.getHardcoverId(), "query selects books with an id");
        try {
            final List<SourceAuthor> credits = hardcover.fetchByHardcoverId(hardcoverId)
                .map(SourceBook::authors)
                .orElse(null);
            if (credits != null) {
                upserts.applyCredits(book.getBookId(), credits);
            }
            markChecked(book);
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                return true;
            }
            if (ex.getStatusCode().is4xxClientError()) {
                markChecked(book);
            } else {
                logFailure(book, ex);
            }
        } catch (WebClientException | DataAccessException ex) {
            logFailure(book, ex);
        }
        pause();
        return false;
    }

    private void markChecked(final Book book) {
        books.markCreditsChecked(book.getBookId(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static void logFailure(final Book book, final RuntimeException ex) {
        LOG.warn("catalog.credit-backfill failed bookId={} ({}), skipping it",
            book.getBookId(), ex.getClass().getSimpleName());
    }

    // PMD.DoNotUseThreads: paces Hardcover calls and restores the interrupt flag after an interrupted sleep.
    @SuppressWarnings("PMD.DoNotUseThreads")
    private void pause() {
        try {
            Thread.sleep(properties.pause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
