package com.betterreads.features.bookdetail;

import com.betterreads.book.BookPromotedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes a promoted book to its open detail-page streams after commit, so the read sees the committed row. */
@Component
class BookUpdateListener {

    private final BookDetailService bookDetailService;

    private final BookUpdateEmitters emitters;

    public BookUpdateListener(final BookDetailService bookDetailService, final BookUpdateEmitters emitters) {
        this.bookDetailService = bookDetailService;
        this.emitters = emitters;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBookPromoted(final BookPromotedEvent event) {
        bookDetailService.findByKey(event.dedupKey())
            .ifPresent(detail -> emitters.publish(event.dedupKey(), detail));
    }
}
