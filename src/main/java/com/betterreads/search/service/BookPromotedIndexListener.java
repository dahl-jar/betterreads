package com.betterreads.search.service;

import java.util.List;

import com.betterreads.catalog.dto.BookIndexView;
import com.betterreads.catalog.event.BookPromotedEvent;
import com.betterreads.catalog.service.read.BookIndexViewReader;
import com.betterreads.common.util.LogSanitizer;
import com.betterreads.search.event.BookIndexedEvent;
import com.betterreads.search.mapper.BookSearchDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Indexes a book into Meilisearch after its promotion commits.
 *
 * <p>Runs after commit, so an indexing failure does not roll back the promotion and the committed
 * row is visible to the read. An index outage drops this one book until the nightly reconcile picks
 * it up.
 */
@Component
@RequiredArgsConstructor
public class BookPromotedIndexListener {

    private static final Logger LOG = LoggerFactory.getLogger(BookPromotedIndexListener.class);

    private final BookIndexViewReader indexViews;

    private final BookSearchDocumentMapper mapper;

    private final BookSearchService searchService;

    private final ApplicationEventPublisher events;

    /** Indexes the promoted book identified by the event. */
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
