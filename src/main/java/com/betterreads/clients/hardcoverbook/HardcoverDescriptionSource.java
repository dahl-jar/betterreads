package com.betterreads.clients.hardcoverbook;

import java.util.Optional;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.booksource.DescriptionSource;
import com.betterreads.booksource.SourceBook;
import org.springframework.stereotype.Component;

/** Reads a book's description from its Hardcover record. The id pins the exact record, so the title is not checked. */
@Component
class HardcoverDescriptionSource implements DescriptionSource {

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
