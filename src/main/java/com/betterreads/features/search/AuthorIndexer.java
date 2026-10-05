package com.betterreads.features.search;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.betterreads.book.BookPromotedEvent;
import com.betterreads.bookindex.AuthorIndexView;
import com.betterreads.bookindex.AuthorIndexViewReader;
import com.betterreads.logging.LogSanitizer;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Order(SearchStartup.AUTHOR_FILL)
class AuthorIndexer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorIndexer.class);

    private static final Duration LOOKBACK = Duration.ofDays(2);

    private static final int PAGE_SIZE = 500;

    private final AuthorIndexViewReader views;

    private final AuthorSearchDocumentMapper mapper;

    private final AuthorSearchService searchService;

    AuthorIndexer(
        final AuthorIndexViewReader views,
        final AuthorSearchDocumentMapper mapper,
        final AuthorSearchService searchService
    ) {
        this.views = views;
        this.mapper = mapper;
        this.searchService = searchService;
    }

    @Override
    public void run(final ApplicationArguments args) {
        try {
            if (searchService.isEmpty()) {
                final int indexed = Stream.iterate(
                        views.page(0, PAGE_SIZE),
                        page -> !page.isEmpty(),
                        page -> views.page(page.getLast().authorId(), PAGE_SIZE))
                    .mapToInt(this::index)
                    .sum();
                LOG.info("search.author-fill indexed {} authors", indexed);
            }
        } catch (SearchIndexException | MeilisearchException ex) {
            LOG.warn("search.author-fill failed ({})", ex.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBookPromoted(final BookPromotedEvent event) {
        try {
            reindex(views.authorIdsOfBook(event.dedupKey()));
        } catch (SearchIndexException ex) {
            LOG.warn("search.author-index failed key={} ({})",
                LogSanitizer.forLog(event.dedupKey()), ex.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${betterreads.search.author-reconcile-cron:0 40 3 * * *}")
    public void reconcile() {
        final List<Long> changed = views.authorIdsChangedSince(OffsetDateTime.now(ZoneOffset.UTC).minus(LOOKBACK));
        pages(changed).forEach(this::reindex);
        final int removed = pages(searchService.indexedIds()).mapToInt(this::removeOutOfScope).sum();
        LOG.info("search.author-reconcile indexed {} authors, removed {}", changed.size(), removed);
    }

    private static Stream<List<Long>> pages(final List<Long> ids) {
        return IntStream.iterate(0, start -> start < ids.size(), start -> start + PAGE_SIZE)
            .mapToObj(start -> ids.subList(start, Math.min(start + PAGE_SIZE, ids.size())));
    }

    private int removeOutOfScope(final List<Long> authorIds) {
        final Set<Long> kept = views.withPrimaryCredit(authorIds);
        final List<Long> gone = authorIds.stream().filter(authorId -> !kept.contains(authorId)).toList();
        searchService.deleteAll(gone);
        return gone.size();
    }

    private void reindex(final Collection<Long> authorIds) {
        final List<AuthorIndexView> inScope = views.forAuthors(authorIds);
        index(inScope);
        final Set<Long> kept = inScope.stream().map(AuthorIndexView::authorId).collect(Collectors.toSet());
        searchService.deleteAll(authorIds.stream().filter(authorId -> !kept.contains(authorId)).toList());
    }

    private int index(final List<AuthorIndexView> page) {
        searchService.index(page.stream().map(mapper::toDocument).toList());
        return page.size();
    }
}
