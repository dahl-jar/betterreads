package com.betterreads.features.metadatacheck;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookSubject;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.CatalogGenres;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.clients.websearch.CheckOutcome;
import com.betterreads.clients.websearch.CheckRun;
import com.betterreads.clients.websearch.CheckedBook;
import com.betterreads.clients.websearch.FieldOutcome;
import com.betterreads.clients.websearch.MetadataCheckClient;
import com.betterreads.clients.websearch.MetadataCheckRequest;
import com.betterreads.clients.websearch.SearchUsage;
import com.betterreads.clients.websearch.SourceGroup;
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
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final List<Book> due = books.findDueForCheck(now, PageRequest.ofSize(properties.maxBooksPerRun()));
        LOG.info("catalog.metadata-check checking books={}", due.size());
        if (due.isEmpty()) {
            return;
        }
        final StoredNames names = new StoredNames(books.findSeriesNames(), books.findAuthorNames());
        final Summary summary = new Summary(due.size());
        for (final Batch batch : batches(due)) {
            final CheckOutcome outcome = client.check(
                batch.books().stream().map(this::request).toList(), batch.group());
            switch (outcome) {
                case CheckOutcome.Checked checked -> apply(batch.books(), checked.run(), names, summary);
                case CheckOutcome.BatchFailed failed -> {
                    LOG.warn("catalog.metadata-check batch failed group={} books={} reason={}", batch.group(),
                        batch.books().size(), LogSanitizer.forLog(failed.reason()));
                    defer(batch.books(), now.plus(properties.retryAfterFailure()), summary);
                }
                case CheckOutcome.RunHalted halted -> {
                    LOG.warn("catalog.metadata-check stopped reason={}", LogSanitizer.forLog(halted.reason()));
                    defer(batch.books(), now.plus(properties.retryAfterFailure()), summary);
                    summary.log();
                    return;
                }
            }
        }
        summary.log();
    }

    private List<Batch> batches(final List<Book> due) {
        final Map<SourceGroup, List<Book>> byGroup = due.stream().collect(Collectors.groupingBy(
            MetadataCheckService::groupOf, () -> new EnumMap<>(SourceGroup.class), Collectors.toList()));
        final int size = properties.batchSize();
        return byGroup.entrySet().stream()
            .flatMap(group -> IntStream.iterate(0, start -> start < group.getValue().size(), start -> start + size)
                .mapToObj(start -> new Batch(group.getKey(),
                    group.getValue().subList(start, Math.min(start + size, group.getValue().size())))))
            .toList();
    }

    private void apply(final List<Book> batch, final CheckRun run, final StoredNames names, final Summary summary) {
        final SearchUsage usage = run.usage();
        LOG.info("catalog.metadata-check batch books={} turns={} costUsd={} durationMs={} deniedCalls={}",
            batch.size(), usage.turns(), dollars(usage.costUsd()), usage.durationMs(), usage.deniedCalls());
        summary.add(usage);
        final OffsetDateTime retryAt = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.retryAfterUnconfirmed());
        for (final Book book : batch) {
            final Optional<CheckedBook> checked = Optional.ofNullable(run.books().get(book.getBookId()));
            checked.ifPresent(answer -> {
                logAnswer(book.getBookId(), answer);
                summary.add(answer);
            });
            final VerifiedMetadata metadata = names.withStoredSpelling(
                checked.map(CheckedBook::metadata).orElse(VerifiedMetadata.NONE), book.getTitle());
            if (metadata.isEmpty()) {
                defer(List.of(book), retryAt, summary);
            } else if (applyVerified(book.getBookId(), metadata)) {
                summary.verified++;
            }
        }
    }

    private boolean applyVerified(final long bookId, final VerifiedMetadata metadata) {
        try {
            upsert.applyVerified(bookId, metadata, properties.checkVersion());
            return true;
        } catch (IllegalArgumentException | DataAccessException ex) {
            LOG.warn("catalog.metadata-check could not apply bookId={} ({})", bookId, ex.getClass().getSimpleName());
            return false;
        }
    }

    private void defer(final List<Book> deferred, final OffsetDateTime retryAt, final Summary summary) {
        deferred.forEach(book -> {
            try {
                if (upsert.deferMetadataCheck(book.getBookId(), retryAt, properties.maxAttempts())) {
                    summary.deferred++;
                } else {
                    summary.gaveUp++;
                }
            } catch (IllegalArgumentException | DataAccessException ex) {
                LOG.warn("catalog.metadata-check could not defer bookId={} ({})", book.getBookId(),
                    ex.getClass().getSimpleName());
            }
        });
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
            seriesName == null ? List.of() : books.findSeriesBooks(seriesName, book.getBookId()),
            book.getDescription());
    }

    private static SourceGroup groupOf(final Book book) {
        return SourceGroup.of(CatalogGenres.reduceToCanonical(
            book.getSubjects().stream().map(BookSubject::getSubject).toList()));
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

    private record Batch(SourceGroup group, List<Book> books) {
    }

    private static final class Summary {

        private final int books;

        private final Map<FieldOutcome, Integer> outcomes = new EnumMap<>(FieldOutcome.class);

        private final Map<String, Integer> unreachable = new TreeMap<>();

        private int verified;

        private int deferred;

        private int gaveUp;

        private int turns;

        private double costUsd;

        Summary(final int books) {
            this.books = books;
        }

        void add(final SearchUsage usage) {
            turns += usage.turns();
            costUsd += usage.costUsd();
        }

        void add(final CheckedBook checked) {
            checked.outcomes().values().forEach(outcome -> outcomes.merge(outcome, 1, Integer::sum));
            checked.unreachableHosts().forEach(host -> unreachable.merge(host, 1, Integer::sum));
        }

        void log() {
            LOG.info("catalog.metadata-check done books={} verified={} deferred={} gaveUp={} turns={} costUsd={}"
                    + " outcomes={} unreachable={}", books, verified, deferred, gaveUp, turns, dollars(costUsd),
                counts(outcomes), LogSanitizer.forLog(counts(unreachable)));
        }

        private static String counts(final Map<?, Integer> counts) {
            return counts.entrySet().stream()
                .map(count -> count.getKey() + ":" + count.getValue())
                .collect(Collectors.joining(","));
        }
    }
}
