package com.betterreads.search.service;

import java.util.List;

import com.betterreads.catalog.event.BookPromotedEvent;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.common.util.LogSanitizer;
import com.betterreads.search.mapper.BookSearchDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Indexes a book into Meilisearch after its promotion commits, so it becomes searchable within
 * seconds instead of at the next nightly reconcile.
 *
 * <p>Runs after commit so an indexing failure never rolls back the promotion and the committed row
 * is visible to the read. An index outage drops this one book until the reconcile heals it.
 */
@Component
@RequiredArgsConstructor
public class BookPromotedIndexListener {

    private static final Logger LOG = LoggerFactory.getLogger(BookPromotedIndexListener.class);

    private final BookRepository books;

    private final BookSearchDocumentMapper mapper;

    private final BookSearchService searchService;

    /** Indexes the promoted book identified by the event. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBookPromoted(final BookPromotedEvent event) {
        books.findByDedupKey(event.dedupKey())
            .ifPresentOrElse(
                book -> searchService.index(List.of(mapper.toDocument(book))),
                () -> LOG.warn("search.index promoted book vanished before indexing key={}",
                    LogSanitizer.forLog(event.dedupKey())));
    }
}
