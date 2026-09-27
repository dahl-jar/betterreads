package com.betterreads.features.bookstaging;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.BookSourceClient;
import com.betterreads.booksource.SourceBook;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

final class StubSourceClients {

    private static final int HTTP_SERVER_ERROR = 503;

    private StubSourceClients() {
    }

    static WebClientResponseException serviceUnavailable() {
        return WebClientResponseException.create(
            HTTP_SERVER_ERROR, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);
    }

    static BookSourceClient emptyFor(final BookFieldSource source) {
        return new StubClient(source);
    }

    static BookSourceClient unavailableByIsbn(final BookFieldSource source) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByIsbn(final String isbn) {
                throw serviceUnavailable();
            }
        };
    }

    static BookSourceClient malformedByIsbn(final BookFieldSource source) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByIsbn(final String isbn) {
                throw new IllegalStateException("malformed response from " + source);
            }
        };
    }

    static BookSourceClient stubByIsbn(
        final BookFieldSource source, final String matchIsbn, final @Nullable SourceBook hit) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByIsbn(final String isbn) {
                return matchIsbn.equals(isbn) ? Optional.ofNullable(hit) : Optional.empty();
            }
        };
    }

    static BookSourceClient stubByTitleAuthor(
        final BookFieldSource source, final String matchTitle, final String matchAuthor,
        final SourceBook hit) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
                return matchTitle.equals(title) && matchAuthor.equals(author)
                    ? Optional.of(hit) : Optional.empty();
            }
        };
    }

    static BookSourceClient recordingIsbnCalls(
        final BookFieldSource source, final List<BookFieldSource> calls) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByIsbn(final String isbn) {
                calls.add(source);
                return Optional.empty();
            }
        };
    }

    static class StubClient implements BookSourceClient {

        private final BookFieldSource sourceId;

        StubClient(final BookFieldSource sourceId) {
            this.sourceId = sourceId;
        }

        @Override
        public BookFieldSource source() {
            return sourceId;
        }

        @Override
        public Optional<SourceBook> fetchByIsbn(final String isbn) {
            return Optional.empty();
        }

        @Override
        public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
            return Optional.empty();
        }
    }
}
