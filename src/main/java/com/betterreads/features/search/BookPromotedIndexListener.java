package com.betterreads.features.search;

import java.util.List;

import com.betterreads.book.BookPromotedEvent;
import com.betterreads.bookindex.BookIndexView;
import com.betterreads.bookindex.BookIndexViewReader;
import com.betterreads.logging.LogSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Indexes a book once its promotion commits.
 *
 * <p>After commit, a Meilisearch failure can't roll back the promotion and the new row is visible
 * to the read. A book missed during an outage waits for the nightly reconcile.
 */
@Component
@RequiredArgsConstructor
class BookPromotedIndexListener {

    private static final Logger LOG = LoggerFactory.getLogger(BookPromotedIndexListener.class);

    private final BookIndexViewReader indexViews;

    private final BookSearchDocumentMapper mapper;

    private final BookSearchService searchService;

    private final ApplicationEventPublisher events;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBookPromoted(final BookPromotedEvent event) {
        indexViews.indexViewByKey(event.dedupKey())
            .ifPresentOrElse(
                this::indexAndAnnounce,
                () -> LOG.warn("search.index promoted book vanished before indexing key={}",
                    LogSanitizer.forLog(event.dedupKey())));
    }

    private void indexAndAnnounce(final BookIndexView book) {
        searchService.index(List.of(mapper.toDocument(book)));
        events.publishEvent(new BookIndexedEvent(book.dedupKey()));
    }
}
