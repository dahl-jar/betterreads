package com.betterreads.features.descriptionbackfill;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookDetailCache;
import com.betterreads.bookdescription.DescriptionSelector;
import com.betterreads.booksource.DescriptionLookup;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Replaces thin descriptions of promoted books with a stronger one from the description sources. The
 * slice size bounds the iTunes calls per run.
 */
@Service
class DescriptionBackfillService {

    private static final Logger LOG = LoggerFactory.getLogger(DescriptionBackfillService.class);

    private static final int THIN_DESCRIPTION_LENGTH = 200;

    private static final int SLICE_SIZE = 50;

    private final BookDescriptionRepository books;

    private final DescriptionSelector selector;

    private final CacheManager cacheManager;

    DescriptionBackfillService(
        final BookDescriptionRepository books,
        final DescriptionSelector selector,
        final CacheManager cacheManager
    ) {
        this.books = books;
        this.selector = selector;
        this.cacheManager = cacheManager;
    }

    public void backfillSlice() {
        final List<Book> candidates = books.findThinDescriptions(
            THIN_DESCRIPTION_LENGTH, PageRequest.ofSize(SLICE_SIZE));
        LOG.info("catalog.description-backfill resolving books={}", candidates.size());
        candidates.forEach(this::backfillOne);
    }

    public void fullSweep() {
        int page = 0;
        List<Book> slice = books.findAllKeyedBooks(PageRequest.of(page, SLICE_SIZE));
        while (!slice.isEmpty()) {
            LOG.info("catalog.description-sweep page={} books={}", page, slice.size());
            slice.forEach(this::backfillOne);
            page++;
            slice = books.findAllKeyedBooks(PageRequest.of(page, SLICE_SIZE));
        }
    }

    private void backfillOne(final Book book) {
        final OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            selector.bestDescription(lookupFor(book), book.getDescription()).ifPresentOrElse(
                description -> {
                    books.updateDescription(book.getBookId(), description, checkedAt);
                    BookDetailCache.evict(cacheManager, book.getDedupKey());
                },
                () -> books.markDescriptionChecked(book.getBookId(), checkedAt));
        } catch (WebClientException | DataAccessException ex) {
            LOG.warn("catalog.description-backfill failed for bookId={} ({}), skipping it",
                book.getBookId(), ex.getClass().getSimpleName());
        }
    }

    private static DescriptionLookup lookupFor(final Book book) {
        return new DescriptionLookup(
            book.getWikidataQid(), book.getIsbn(), book.getTitle(), firstAuthorName(book),
            book.getOpenLibraryWorkKey(), book.getHardcoverId());
    }

    private static @Nullable String firstAuthorName(final Book book) {
        return book.getAuthors().stream().map(Author::getName).findFirst().orElse(null);
    }
}
