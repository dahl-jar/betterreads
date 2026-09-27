package com.betterreads.clients.googlebooks;

import java.util.Optional;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.http.WebClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Google Books REST API client.
 *
 * <p>4xx responses resolve to empty, 5xx responses and network failures throw.
 *
 * <p>{@code /volumes} search ranks the latest reprint of a title first, so
 * {@code fetchByTitleAuthor} returns that reprint's date, publisher and ISBN.
 */
@Component
class GoogleBooksClientImpl implements GoogleBooksClient {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleBooksClientImpl.class);

    private static final String VOLUMES_PATH = "/volumes";

    private final WebClient googleBooksWebClient;

    private final GoogleBooksMapper mapper;

    GoogleBooksClientImpl(
        final WebClient googleBooksWebClient,
        final GoogleBooksMapper mapper
    ) {
        this.googleBooksWebClient = googleBooksWebClient;
        this.mapper = mapper;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.GOOGLE_BOOKS;
    }

    @Override
    public Optional<SourceBook> fetchByIsbn(final String isbn) {
        return search("isbn:" + stripQuotes(isbn)).map(mapper::toSourceBook);
    }

    @Override
    public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
        final String query = "intitle:\"" + stripQuotes(title) + "\""
            + " inauthor:\"" + stripQuotes(author) + "\"";
        return search(query).map(mapper::toSourceBook);
    }

    private Optional<GoogleBooksVolume> search(final String query) {
        return WebClients.emptyOn4xx(() -> {
            final GoogleBooksSearchResponse response = googleBooksWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(VOLUMES_PATH)
                    .queryParam("q", query)
                    .queryParam("maxResults", 1)
                    .build())
                .retrieve()
                .bodyToMono(GoogleBooksSearchResponse.class)
                .block();
            return Optional.ofNullable(response)
                .map(GoogleBooksSearchResponse::items)
                .flatMap(items -> items.stream().findFirst());
        }, LOG, "Google Books search query=" + query);
    }

    /**
     * A double quote in a title or author closes the {@code intitle:"..."} phrase early and turns
     * the rest into query operators, so quotes are stripped, and backslashes too since they can
     * re-open quoting.
     */
    private static String stripQuotes(final String input) {
        return input.replace("\\", "").replace("\"", "");
    }
}
