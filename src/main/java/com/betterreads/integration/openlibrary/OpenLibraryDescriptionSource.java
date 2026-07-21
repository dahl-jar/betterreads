package com.betterreads.integration.openlibrary;

import java.util.Optional;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.port.DescriptionLookup;
import com.betterreads.catalog.service.source.port.DescriptionSource;
import com.betterreads.catalog.service.source.model.SourceBook;
import org.springframework.stereotype.Component;

/**
 * Resolves a book's description from its OpenLibrary work record.
 *
 * <p>The work key identifies the record exactly, so no title check is needed.
 */
@Component
public class OpenLibraryDescriptionSource implements DescriptionSource {

    private final OpenLibraryClient openLibraryClient;

    public OpenLibraryDescriptionSource(final OpenLibraryClient openLibraryClient) {
        this.openLibraryClient = openLibraryClient;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.OPEN_LIBRARY;
    }

    @Override
    public Optional<String> fetch(final DescriptionLookup lookup) {
        final String workKey = lookup.openLibraryWorkKey();
        if (workKey == null || workKey.isBlank()) {
            return Optional.empty();
        }
        return openLibraryClient.fetchByWorkKey(workKey).map(SourceBook::description);
    }
}
