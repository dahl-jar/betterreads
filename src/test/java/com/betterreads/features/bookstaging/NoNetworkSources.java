package com.betterreads.features.bookstaging;

import com.betterreads.bookdescription.DescriptionSelector;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** A null stub response throws a 503, so the source stays unresolved like in a transient outage. */
@TestConfiguration
class NoNetworkSources {

    static final AtomicReference<@Nullable SourceBook> HARDCOVER_RESPONSE = new AtomicReference<>();

    static final AtomicReference<@Nullable SourceBook> OPEN_LIBRARY_RESPONSE = new AtomicReference<>();

    static void reset() {
        HARDCOVER_RESPONSE.set(null);
        OPEN_LIBRARY_RESPONSE.set(null);
    }

    @Bean
    @Primary
    SourceCollector noNetworkSourceCollector(final SourceMerger merger) {
        return new SourceCollector(
            merger,
            List.of(
                new ControllableClient(BookFieldSource.HARDCOVER, HARDCOVER_RESPONSE),
                new ControllableClient(BookFieldSource.OPEN_LIBRARY, OPEN_LIBRARY_RESPONSE),
                StubSourceClients.emptyFor(BookFieldSource.WIKIDATA)),
            new DescriptionSelector(List.of()), Runnable::run);
    }

    private static final class ControllableClient extends StubSourceClients.StubClient {

        private final AtomicReference<@Nullable SourceBook> response;

        ControllableClient(
            final BookFieldSource reportedSource,
            final AtomicReference<@Nullable SourceBook> response
        ) {
            super(reportedSource);
            this.response = response;
        }

        @Override
        public Optional<SourceBook> fetchByIsbn(final String isbn) {
            final SourceBook book = response.get();
            if (book == null) {
                throw StubSourceClients.serviceUnavailable();
            }
            return isbn.equals(book.isbn13()) ? Optional.of(book) : Optional.empty();
        }
    }
}
