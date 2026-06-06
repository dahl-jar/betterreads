package com.betterreads.catalog.read.sse;

import com.betterreads.catalog.event.BookPromotedEvent;
import com.betterreads.catalog.service.read.BookReadService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes the filled-in book to any open detail-page stream after its promotion commits.
 *
 * <p>Runs after commit so the read sees the committed row. A detail page opened on the cold book
 * receives the {@code book-updated} event and patches its missing sections in place.
 */
@Component
public class BookUpdateListener {

    private final BookReadService bookReadService;

    private final BookUpdateEmitters emitters;

    public BookUpdateListener(final BookReadService bookReadService, final BookUpdateEmitters emitters) {
        this.bookReadService = bookReadService;
        this.emitters = emitters;
    }

    /** Sends the promoted book to its open detail-page streams. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBookPromoted(final BookPromotedEvent event) {
        bookReadService.findByKey(event.dedupKey())
            .ifPresent(detail -> emitters.publish(event.dedupKey(), detail));
    }
}
