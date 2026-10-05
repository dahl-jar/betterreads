package com.betterreads.features.metadatacheck;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.clients.websearch.CheckRun;
import com.betterreads.clients.websearch.CheckedBook;
import com.betterreads.clients.websearch.MetadataCheckClient;
import com.betterreads.clients.websearch.MetadataCheckRequest;
import com.betterreads.clients.websearch.SearchUsage;
import com.betterreads.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
class MetadataCheckService {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataCheckService.class);

    private static final int MAX_LOGGED_LINE = 4000;

    private final MetadataCheckRepository books;

    private final MetadataCheckClient client;

    private final BookUpsertService upsert;

    private final MetadataCheckProperties properties;

    MetadataCheckService(
        final MetadataCheckRepository books,
        final MetadataCheckClient client,
        final BookUpsertService upsert,
        final MetadataCheckProperties properties
    ) {
        this.books = books;
        this.client = client;
        this.upsert = upsert;
        this.properties = properties;
    }

    public void checkDueBooks() {
        final List<Book> due = books.findDueForCheck(PageRequest.ofSize(properties.maxBooksPerRun()));
        LOG.info("catalog.metadata-check checking books={}", due.size());
        if (due.isEmpty()) {
            return;
        }
        final StoredNames names = new StoredNames(books.findSeriesNames(), books.findAuthorNames());
        int confirmed = 0;
        int turns = 0;
        double costUsd = 0;
        for (int start = 0; start < due.size(); start += properties.batchSize()) {
            final List<Book> batch = due.subList(start, Math.min(start + properties.batchSize(), due.size()));
            final Optional<CheckRun> result = client.check(batch.stream().map(this::request).toList());
            if (result.isEmpty()) {
                LOG.warn("catalog.metadata-check stopped, the search failed");
                return;
            }
            final CheckRun run = result.get();
            final SearchUsage usage = run.usage();
            LOG.info("catalog.metadata-check batch books={} turns={} costUsd={} durationMs={} deniedCalls={}",
                batch.size(), usage.turns(), dollars(usage.costUsd()), usage.durationMs(), usage.deniedCalls());
            for (final Book book : batch) {
                final Optional<CheckedBook> checked = Optional.ofNullable(run.books().get(book.getBookId()));
                checked.ifPresent(answer -> logAnswer(book.getBookId(), answer));
                apply(book.getBookId(), names.withStoredSpelling(
                    checked.map(CheckedBook::metadata).orElse(VerifiedMetadata.NONE), book.getTitle()));
                if (checked.filter(CheckedBook::confirmed).isPresent()) {
                    confirmed++;
                }
            }
            turns += usage.turns();
            costUsd += usage.costUsd();
        }
        LOG.info("catalog.metadata-check done books={} confirmed={} unconfirmed={} turns={} costUsd={}",
            due.size(), confirmed, due.size() - confirmed, turns, dollars(costUsd));
    }

    private void apply(final long bookId, final VerifiedMetadata metadata) {
        try {
            upsert.applyVerified(bookId, metadata);
        } catch (IllegalArgumentException | DataAccessException ex) {
            LOG.warn("catalog.metadata-check could not apply bookId={} ({})", bookId, ex.getClass().getSimpleName());
        }
    }

    private MetadataCheckRequest request(final Book book) {
        final SeriesEntry universe = book.getSeries().stream().skip(1).findFirst().orElse(null);
        final String seriesName = book.getSeriesName();
        return new MetadataCheckRequest(
            book.getBookId(),
            book.getTitle(),
            Author.names(book.getAuthors()),
            book.getFirstPublishYear(),
            seriesName,
            book.getSeriesPosition(),
            book.getIsbn(),
            universe,
            seriesName == null ? List.of() : books.findSeriesBooks(seriesName, book.getBookId()));
    }

    private static void logAnswer(final long bookId, final CheckedBook checked) {
        final String outcomes = checked.outcomes().entrySet().stream()
            .map(outcome -> outcome.getKey() + ":" + outcome.getValue())
            .collect(Collectors.joining(","));
        final String line = "catalog.metadata-check bookId=" + bookId + " outcomes=" + outcomes
            + " answer=" + checked.answer();
        LOG.info("{}", LogSanitizer.forLog(line.substring(0, Math.min(line.length(), MAX_LOGGED_LINE))));
    }

    private static String dollars(final double costUsd) {
        return String.format(Locale.ROOT, "%.2f", costUsd);
    }
}
