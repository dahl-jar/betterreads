package com.betterreads.features.bookstaging;

import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
class BookStager {

    private static final Logger LOG = LoggerFactory.getLogger(BookStager.class);

    private final SourceCollector sourceCollector;

    private final PendingBookService pendingBookService;

    BookStager(final SourceCollector sourceCollector, final PendingBookService pendingBookService) {
        this.sourceCollector = sourceCollector;
        this.pendingBookService = pendingBookService;
    }

    void stage(final SourceBook seed) {
        try {
            final MergedBook merged = sourceCollector.collectFor(seed);
            final String dedupKey = merged.book().dedupKey();
            if (dedupKey != null) {
                pendingBookService.stage(merged);
                pendingBookService.promoteNow(dedupKey, merged);
            }
        } catch (DataAccessException ex) {
            LOG.warn("catalog.search staging failed for source {} ({}), skipping it",
                seed.source(), ex.getClass().getSimpleName());
        }
    }
}
