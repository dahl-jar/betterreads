package com.betterreads.search.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betterreads.catalog.service.pipeline.SearchMissStager;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.dto.SearchOutcome;
import com.betterreads.search.service.BookSearchService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The search endpoint resolves a query's series and author in the background on the first page of
 * every successful search, so an author whose catalog is mostly unstaged still fills in for a later
 * search even when the current search already returns some hits.
 */
class BookSearchControllerTest {

    private static final String QUERY = "robert jordan";

    private static final int FIRST_PAGE = 0;

    private static final int SECOND_PAGE = 20;

    private static final int PAGE_SIZE = 20;

    private static final long SOME_HITS = 5L;

    private final BookSearchService searchService = Mockito.mock(BookSearchService.class);

    private final SearchMissStager stager = Mockito.mock(SearchMissStager.class);

    private final BookSearchController controller = new BookSearchController(searchService, stager);

    @Test
    @DisplayName("the first page stages the query even when the search already returns hits")
    void firstPageStagesEvenWithHits() {
        stub(FIRST_PAGE, outcome(SOME_HITS, FIRST_PAGE, false));

        controller.search(QUERY, FIRST_PAGE, PAGE_SIZE);

        verify(stager).stage(QUERY);
    }

    @Test
    @DisplayName("a load-more page does not stage again")
    void loadMorePageDoesNotStage() {
        stub(SECOND_PAGE, outcome(SOME_HITS, SECOND_PAGE, false));

        controller.search(QUERY, SECOND_PAGE, PAGE_SIZE);

        verify(stager, never()).stage(QUERY);
    }

    @Test
    @DisplayName("a degraded search does not stage")
    void degradedSearchDoesNotStage() {
        stub(FIRST_PAGE, outcome(0L, FIRST_PAGE, true));

        controller.search(QUERY, FIRST_PAGE, PAGE_SIZE);

        verify(stager, never()).stage(QUERY);
    }

    private void stub(final int offset, final SearchOutcome outcome) {
        when(searchService.searchOutcome(QUERY, offset, PAGE_SIZE)).thenReturn(outcome);
    }

    private static SearchOutcome outcome(final long totalHits, final int offset, final boolean degraded) {
        return new SearchOutcome(new BookSearchResult(List.of(), totalHits, offset, PAGE_SIZE), degraded);
    }
}
