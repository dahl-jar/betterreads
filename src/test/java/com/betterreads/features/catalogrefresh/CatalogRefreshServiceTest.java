package com.betterreads.features.catalogrefresh;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.bookdiscovery.BookDiscovery;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class CatalogRefreshServiceTest {

    private static final String JORDAN = "Robert Jordan";

    private static final String SANDERSON = "Brandon Sanderson";

    private final AuthorRepository authors = mock(AuthorRepository.class);

    private final BookDiscovery discovery = mock(BookDiscovery.class);

    private final DueSeriesRefresher dueSeries = mock(DueSeriesRefresher.class);

    private CatalogRefreshService service(final boolean authorsEnabled, final boolean seriesEnabled) {
        return new CatalogRefreshService(authors, discovery, dueSeries,
            CatalogRefreshSamples.properties(authorsEnabled, seriesEnabled));
    }

    @Test
    void shouldRefreshEveryAuthorWhenAuthorsAreEnabled() {
        when(authors.findAll()).thenReturn(List.of(author(JORDAN), author(SANDERSON)));

        service(true, false).refresh();

        verify(discovery).searchAuthorAndStage(JORDAN);
        verify(discovery).searchAuthorAndStage(SANDERSON);
    }

    @Test
    void shouldSkipAuthorsWhenAuthorsAreDisabled() {
        when(authors.findAll()).thenReturn(List.of(author(JORDAN)));

        service(false, true).refresh();

        verify(discovery, never()).searchAuthorAndStage(anyString());
    }

    @Test
    @DisplayName("a failure on one author does not stop the remaining authors")
    void oneFailureDoesNotStopTheRest() {
        when(authors.findAll()).thenReturn(List.of(author(JORDAN), author(SANDERSON)));
        doThrow(new DataAccessResourceFailureException("boom"))
            .when(discovery).searchAuthorAndStage(JORDAN);

        service(true, false).refresh();

        verify(discovery).searchAuthorAndStage(SANDERSON);
    }

    @Test
    void shouldRefreshDueSeriesWhenSeriesAreEnabled() {
        service(false, true).refresh();

        verify(dueSeries).refresh();
    }

    @Test
    void shouldSkipSeriesWhenSeriesAreDisabled() {
        service(true, false).refresh();

        verify(dueSeries, never()).refresh();
    }

    private static Author author(final String name) {
        final Author author = new Author();
        author.setName(name);
        return author;
    }
}
