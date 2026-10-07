package com.betterreads.features.search;

import java.util.List;

import com.betterreads.book.BookChangedEvent;
import com.betterreads.book.BookDetailCache;
import com.betterreads.bookindex.BookIndexView;
import com.betterreads.bookindex.BookIndexViewReader;
import com.betterreads.logging.LogSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class BookChangedIndexListener {

    private static final Logger LOG = LoggerFactory.getLogger(BookChangedIndexListener.class);

    private final BookIndexViewReader indexViews;

    private final BookSearchDocumentMapper mapper;

    private final BookSearchService searchService;

    private final ApplicationEventPublisher events;

    private final CacheManager cacheManager;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBookChanged(final BookChangedEvent event) {
        indexViews.indexViewsByIds(List.of(event.bookId())).stream()
            .findFirst()
            .ifPresentOrElse(
                this::evictAndIndex,
                () -> LOG.warn("search.index changed book vanished before indexing bookId={}", event.bookId()));
    }

    private void evictAndIndex(final BookIndexView book) {
        BookDetailCache.evict(cacheManager, book.dedupKey());
        try {
            searchService.index(List.of(mapper.toDocument(book)));
        } catch (SearchIndexException ex) {
            LOG.warn("search.index reindex failed key={} ({}), the nightly reconcile retries it",
                LogSanitizer.forLog(book.dedupKey()), ex.getClass().getSimpleName());
            return;
        }
        events.publishEvent(new BookIndexedEvent(book.dedupKey()));
    }
}
