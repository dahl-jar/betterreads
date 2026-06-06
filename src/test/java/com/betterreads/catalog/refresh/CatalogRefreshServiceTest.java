package com.betterreads.catalog.refresh;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.service.pipeline.CatalogSearchService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * The daily refresh re-resolves every known author and series through discovery, so a new book by a
 * known author is staged without a user searching again; one failing entry does not stop the rest.
 */
class CatalogRefreshServiceTest {

    private static final String JORDAN = "Robert Jordan";

    private static final String SANDERSON = "Brandon Sanderson";

    private static final String WHEEL_OF_TIME = "The Wheel of Time";

    private final AuthorRepository authors = mock(AuthorRepository.class);

    private final BookRepository books = mock(BookRepository.class);

    private final CatalogSearchService catalogSearch = mock(CatalogSearchService.class);

    private final CatalogRefreshService service =
        new CatalogRefreshService(authors, books, catalogSearch);

    @Test
    @DisplayName("re-resolves each known author and series through discovery")
    void resolvesEveryAuthorAndSeries() {
        when(authors.findAll()).thenReturn(List.of(author(JORDAN), author(SANDERSON)));
        when(books.findDistinctSeriesNames()).thenReturn(List.of(WHEEL_OF_TIME));

        service.refreshKnownAuthorsAndSeries();

        verify(catalogSearch).searchAuthorAndStage(JORDAN);
        verify(catalogSearch).searchAuthorAndStage(SANDERSON);
        verify(catalogSearch).searchAndStage(WHEEL_OF_TIME);
    }

    @Test
    @DisplayName("a failure on one author does not stop the remaining authors and series")
    void oneFailureDoesNotStopTheRest() {
        when(authors.findAll()).thenReturn(List.of(author(JORDAN), author(SANDERSON)));
        when(books.findDistinctSeriesNames()).thenReturn(List.of(WHEEL_OF_TIME));
        doThrow(new DataAccessResourceFailureException("boom"))
            .when(catalogSearch).searchAuthorAndStage(JORDAN);

        service.refreshKnownAuthorsAndSeries();

        verify(catalogSearch).searchAuthorAndStage(SANDERSON);
        verify(catalogSearch).searchAndStage(WHEEL_OF_TIME);
    }

    private static Author author(final String name) {
        final Author author = new Author();
        author.setName(name);
        return author;
    }
}
