package com.betterreads.features.catalogrefresh;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.BookRepository;
import com.betterreads.bookdiscovery.BookDiscovery;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class CatalogRefreshServiceTest {

    private static final String JORDAN = "Robert Jordan";

    private static final String SANDERSON = "Brandon Sanderson";

    private static final String WHEEL_OF_TIME = "The Wheel of Time";

    private static final String STORMLIGHT = "The Stormlight Archive";

    private static final int HTTP_BAD_GATEWAY = 502;

    private final AuthorRepository authors = mock(AuthorRepository.class);

    private final BookRepository books = mock(BookRepository.class);

    private final BookDiscovery discovery = mock(BookDiscovery.class);

    private final CatalogRefreshService service =
        new CatalogRefreshService(authors, books, discovery);

    @Test
    @DisplayName("re-resolves each known author and series through discovery")
    void resolvesEveryAuthorAndSeries() {
        when(authors.findAll()).thenReturn(List.of(author(JORDAN), author(SANDERSON)));
        when(books.findDistinctSeriesNames()).thenReturn(List.of(WHEEL_OF_TIME));

        service.refresh();

        verify(discovery).searchAuthorAndStage(JORDAN);
        verify(discovery).searchAuthorAndStage(SANDERSON);
        verify(discovery).searchAndStage(WHEEL_OF_TIME);
    }

    @Test
    @DisplayName("a failure on one author does not stop the remaining authors and series")
    void oneFailureDoesNotStopTheRest() {
        when(authors.findAll()).thenReturn(List.of(author(JORDAN), author(SANDERSON)));
        when(books.findDistinctSeriesNames()).thenReturn(List.of(WHEEL_OF_TIME));
        doThrow(new DataAccessResourceFailureException("boom"))
            .when(discovery).searchAuthorAndStage(JORDAN);

        service.refresh();

        verify(discovery).searchAuthorAndStage(SANDERSON);
        verify(discovery).searchAndStage(WHEEL_OF_TIME);
    }

    @Test
    @DisplayName("should keep going when a series lookup fails")
    void shouldContinueWhenSeriesLookupFails() {
        when(authors.findAll()).thenReturn(List.of());
        when(books.findDistinctSeriesNames()).thenReturn(List.of(WHEEL_OF_TIME, STORMLIGHT));
        final WebClientResponseException badGateway = WebClientResponseException.create(
            HTTP_BAD_GATEWAY, "Bad Gateway", HttpHeaders.EMPTY, new byte[0], null);
        doThrow(badGateway).when(discovery).searchAndStage(WHEEL_OF_TIME);

        service.refresh();

        verify(discovery).searchAndStage(STORMLIGHT);
    }

    private static Author author(final String name) {
        final Author author = new Author();
        author.setName(name);
        return author;
    }
}
