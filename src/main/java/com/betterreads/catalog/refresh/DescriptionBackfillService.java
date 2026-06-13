package com.betterreads.catalog.refresh;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookDescriptionRepository;
import com.betterreads.catalog.service.pipeline.DescriptionSelector;
import com.betterreads.catalog.service.source.DescriptionLookup;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Re-resolves a stronger description for already-promoted books with a thin one, a bounded slice per
 * run, from the description sources.
 *
 * <p>The slice and the thin-description threshold bound the work and the iTunes call rate; a run
 * picks the least recently checked candidates and stamps each as checked, so successive runs walk the
 * catalog without re-doing books already tried. The new description is written to its own column with
 * a targeted update, leaving rating and community columns untouched, and the cached detail is evicted.
 * One failing book is logged and skipped.
 */
@Service
public class DescriptionBackfillService {

    private static final Logger LOG = LoggerFactory.getLogger(DescriptionBackfillService.class);

    private static final int THIN_DESCRIPTION_LENGTH = 200;

    private static final int SLICE_SIZE = 50;

    private static final String BOOK_DETAIL_CACHE = "bookDetails";

    private final BookDescriptionRepository books;

    private final DescriptionSelector selector;

    private final CacheManager cacheManager;

    public DescriptionBackfillService(
        final BookDescriptionRepository books,
        final DescriptionSelector selector,
        final CacheManager cacheManager
    ) {
        this.books = books;
        this.selector = selector;
        this.cacheManager = cacheManager;
    }

    /** Re-resolves descriptions for one slice of thin-description books. */
    public void backfillSlice() {
        final List<Book> candidates = books.findDescriptionBackfillCandidates(
            THIN_DESCRIPTION_LENGTH, PageRequest.ofSize(SLICE_SIZE));
        LOG.info("catalog.description-backfill resolving books={}", candidates.size());
        candidates.forEach(this::backfillOne);
    }

    /**
     * Re-resolves every keyed book's description through the description sources, a page at a time
     * until the catalog is exhausted. The selector rewrites only when a source beats the current
     * description, and the rate limiter paces the iTunes calls, so a long run self-throttles.
     */
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
            final Optional<String> better = selector.bestDescription(lookupFor(book), book.getDescription());
            if (better.isPresent()) {
                books.updateDescription(book.getBookId(), better.get(), checkedAt);
                evictDetail(book.getDedupKey());
            } else {
                books.markDescriptionChecked(book.getBookId(), checkedAt);
            }
        } catch (WebClientException | DataAccessException ex) {
            LOG.warn("catalog.description-backfill failed for one book ({}), skipping it",
                ex.getClass().getSimpleName());
        }
    }

    private void evictDetail(final String dedupKey) {
        final Cache cache = cacheManager.getCache(BOOK_DETAIL_CACHE);
        if (cache != null) {
            cache.evict(dedupKey);
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
