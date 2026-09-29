package com.betterreads.features.metadatacheck;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        final StoredNames names = new StoredNames(books.findSeriesNames(), books.findAuthorNames());
        for (int start = 0; start < unchecked.size(); start += properties.batchSize()) {
            final List<Book> batch =
                unchecked.subList(start, Math.min(start + properties.batchSize(), unchecked.size()));
            final Optional<Map<Long, VerifiedMetadata>> checks =
                client.check(batch.stream().map(MetadataCheckService::request).toList());
            if (checks.isEmpty()) {
                LOG.warn("catalog.metadata-check stopped, the search failed");
                return;
            }
            batch.forEach(book -> apply(book.getBookId(), names.withStoredSpelling(
                checks.get().getOrDefault(book.getBookId(), VerifiedMetadata.NONE), book.getTitle())));
        }
    }

    private void apply(final long bookId, final VerifiedMetadata metadata) {
        try {
            upsert.applyVerified(bookId, metadata);
        } catch (IllegalArgumentException | DataAccessException ex) {
            LOG.warn("catalog.metadata-check could not apply bookId={} ({})", bookId, ex.getClass().getSimpleName());
        }
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
