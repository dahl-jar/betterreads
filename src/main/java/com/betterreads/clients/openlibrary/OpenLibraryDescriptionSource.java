package com.betterreads.clients.openlibrary;

import java.util.Optional;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.booksource.DescriptionSource;
import com.betterreads.booksource.SourceBook;
import org.springframework.stereotype.Component;

/** Reads a book's description from its OpenLibrary work record. */
@Component
class OpenLibraryDescriptionSource implements DescriptionSource {

    private final OpenLibraryClient openLibraryClient;

    OpenLibraryDescriptionSource(final OpenLibraryClient openLibraryClient) {
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
