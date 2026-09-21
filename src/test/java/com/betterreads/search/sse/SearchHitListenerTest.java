package com.betterreads.search.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betterreads.search.dto.BookSearchDocument;
import com.betterreads.search.event.BookIndexedEvent;
import com.betterreads.search.service.BookSearchService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SearchHitListenerTest {

    private static final String BOOK_ID = "9780000000001";

    private static final String MATCHING_QUERY = "eye of the world";

    private static final String OTHER_QUERY = "dune";

    private final BookSearchService searchService = Mockito.mock(BookSearchService.class);

    private final SearchHitEmitters emitters = Mockito.mock(SearchHitEmitters.class);

    private final SearchHitListener listener = new SearchHitListener(searchService, emitters);

    @Test
    void shouldPushHitOnlyToQueriesTheBookMatches() {
        final BookSearchDocument hit = BookSearchDocument.builder(BOOK_ID)
            .title("The Eye of the World")
            .authors(List.of("Robert Jordan"))
            .build();
        when(emitters.openQueries()).thenReturn(Set.of(MATCHING_QUERY, OTHER_QUERY));
        when(searchService.hitFor(MATCHING_QUERY, BOOK_ID)).thenReturn(Optional.of(hit));
        when(searchService.hitFor(OTHER_QUERY, BOOK_ID)).thenReturn(Optional.empty());

        listener.onBookIndexed(new BookIndexedEvent(BOOK_ID));

        verify(emitters).publish(MATCHING_QUERY, hit);
        verify(emitters, never()).publish(eq(OTHER_QUERY), any());
    }
}
