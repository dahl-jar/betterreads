package com.betterreads.catalog.service;

import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.mapper.PendingBookMapper;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.RequiredFieldsCheck.MissingFields;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one collected candidate: re-applies the merged view to its row, then promotes it into
 * {@code book} if it now carries every required field, or records what is still missing.
 *
 * <p>Separate bean so each candidate's write runs in its own short transaction through the Spring
 * proxy. The slow source fetches happen in {@link PendingBookService} outside any transaction, so a
 * connection is never held open across an external call.
 */
@Component
public class PendingBookPromoter {

    private static final String STATUS_PROMOTED = "PROMOTED";

    private final PendingBookRepository pendingBooks;

    private final CatalogService catalogService;

    private final RequiredFieldsCheck requiredFields;

    private final PendingBookMapper mapper;

    public PendingBookPromoter(
        final PendingBookRepository pendingBooks,
        final CatalogService catalogService,
        final RequiredFieldsCheck requiredFields,
        final PendingBookMapper mapper
    ) {
        this.pendingBooks = pendingBooks;
        this.catalogService = catalogService;
        this.requiredFields = requiredFields;
        this.mapper = mapper;
    }

    /** Re-applies the collected merge to the candidate and promotes it when it is ready to show. */
    @Transactional
    public void promote(final String dedupKey, final MergedBook collected) {
        final PendingBook row = pendingBooks.findByDedupKey(dedupKey).orElse(null);
        if (row == null) {
            return;
        }
        mapper.applyTo(row, collected);
        final MissingFields missing = requiredFields.check(collected.book());
        if (missing.isReady()) {
            catalogService.upsertFromSource(collected.book());
            row.setStatus(STATUS_PROMOTED);
        } else {
            row.setMissingFields(mapper.join(missing.missing()));
        }
        pendingBooks.save(row);
    }
}
