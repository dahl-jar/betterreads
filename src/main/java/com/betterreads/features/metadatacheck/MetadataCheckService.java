package com.betterreads.features.metadatacheck;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.clients.websearch.MetadataCheckClient;
import com.betterreads.clients.websearch.MetadataCheckRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
class MetadataCheckService {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataCheckService.class);

    private static final Pattern LEADING_ARTICLE = Pattern.compile("^the\\s+");

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

    public void checkNewBooks() {
        final OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(properties.lookbackDays());
        final List<Book> unchecked = books.findUncheckedSince(since, PageRequest.ofSize(properties.maxBooksPerRun()));
        LOG.info("catalog.metadata-check checking books={}", unchecked.size());
        if (unchecked.isEmpty()) {
            return;
        }
        final Map<String, String> storedSeries = books.findSeriesNames().stream()
            .collect(Collectors.toMap(MetadataCheckService::seriesKey, Function.identity(), (first, second) -> first));
        for (int start = 0; start < unchecked.size(); start += properties.batchSize()) {
            final List<Book> batch =
                unchecked.subList(start, Math.min(start + properties.batchSize(), unchecked.size()));
            final Optional<Map<Long, VerifiedMetadata>> checks =
                client.check(batch.stream().map(MetadataCheckService::request).toList());
            if (checks.isEmpty()) {
                LOG.warn("catalog.metadata-check stopped, the search failed");
                return;
            }
            batch.forEach(book -> apply(book.getBookId(),
                withStoredSeries(checks.get().getOrDefault(book.getBookId(), VerifiedMetadata.NONE), storedSeries)));
        }
    }

    private void apply(final long bookId, final VerifiedMetadata metadata) {
        try {
            upsert.applyVerified(bookId, metadata);
        } catch (IllegalArgumentException | DataAccessException ex) {
            LOG.warn("catalog.metadata-check could not apply bookId={} ({})", bookId, ex.getClass().getSimpleName());
        }
    }

    private static VerifiedMetadata withStoredSeries(
        final VerifiedMetadata metadata, final Map<String, String> storedSeries) {
        final String series = metadata.seriesName();
        if (series == null) {
            return metadata;
        }
        return new VerifiedMetadata(metadata.title(), metadata.authors(), metadata.year(),
            storedSeries.getOrDefault(seriesKey(series), series), metadata.seriesPosition(),
            metadata.description(), metadata.isbn13());
    }

    private static String seriesKey(final String name) {
        return LEADING_ARTICLE.matcher(name.toLowerCase(Locale.ROOT).strip()).replaceFirst("")
            .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static MetadataCheckRequest request(final Book book) {
        return new MetadataCheckRequest(
            book.getBookId(),
            book.getTitle(),
            book.getAuthors().stream().map(Author::getName).sorted().toList(),
            book.getFirstPublishYear(),
            book.getSeriesName(),
            book.getSeriesPosition(),
            book.getIsbn());
    }
}
