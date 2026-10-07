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

/**
 * A null Hardcover or OpenLibrary response throws a 503 like a transient outage. A null Wikidata
 * response is a clean miss.
 */
@TestConfiguration
class NoNetworkSources {

    static final AtomicReference<@Nullable SourceBook> HARDCOVER_RESPONSE = new AtomicReference<>();

    static final AtomicReference<@Nullable SourceBook> OPEN_LIBRARY_RESPONSE = new AtomicReference<>();

    static final AtomicReference<@Nullable SourceBook> WIKIDATA_RESPONSE = new AtomicReference<>();

    static void reset() {
        HARDCOVER_RESPONSE.set(null);
        OPEN_LIBRARY_RESPONSE.set(null);
        WIKIDATA_RESPONSE.set(null);
    }

    @Bean
    @Primary
    SourceCollector noNetworkSourceCollector(final SourceMerger merger) {
        return new SourceCollector(
            merger,
            List.of(
                new ControllableClient(BookFieldSource.HARDCOVER, HARDCOVER_RESPONSE, true),
                new ControllableClient(BookFieldSource.OPEN_LIBRARY, OPEN_LIBRARY_RESPONSE, true),
                new ControllableClient(BookFieldSource.WIKIDATA, WIKIDATA_RESPONSE, false)),
            new DescriptionSelector(List.of()), Runnable::run);
    }

    private static final class ControllableClient extends StubSourceClients.StubClient {

        private final AtomicReference<@Nullable SourceBook> response;

        private final boolean failWhenUnset;

        ControllableClient(
            final BookFieldSource reportedSource,
            final AtomicReference<@Nullable SourceBook> response,
            final boolean failWhenUnset
        ) {
            super(reportedSource);
            this.response = response;
            this.failWhenUnset = failWhenUnset;
        }

        @Override
        public Optional<SourceBook> fetchByIsbn(final String isbn) {
            final SourceBook book = response.get();
            if (book == null && failWhenUnset) {
                throw StubSourceClients.serviceUnavailable();
            }
            return Optional.ofNullable(book).filter(found -> isbn.equals(found.isbn13()));
        }
    }
}
