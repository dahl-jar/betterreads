package com.betterreads.integration.hardcover;

import java.util.Optional;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.port.DescriptionLookup;
import com.betterreads.catalog.service.source.port.DescriptionSource;
import com.betterreads.catalog.service.source.model.SourceBook;
import org.springframework.stereotype.Component;

/**
 * Resolves a book's description from its Hardcover book record.
 *
 * <p>The Hardcover id identifies the record exactly, so no title check is needed.
 */
@Component
public class HardcoverDescriptionSource implements DescriptionSource {

    private final HardcoverClient hardcoverClient;

    public HardcoverDescriptionSource(final HardcoverClient hardcoverClient) {
        this.hardcoverClient = hardcoverClient;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.HARDCOVER;
    }

    @Override
    public Optional<String> fetch(final DescriptionLookup lookup) {
        final String hardcoverId = lookup.hardcoverId();
        if (hardcoverId == null || hardcoverId.isBlank()) {
            return Optional.empty();
        }
        return hardcoverClient.fetchByHardcoverId(hardcoverId).map(SourceBook::description);
    }
}
