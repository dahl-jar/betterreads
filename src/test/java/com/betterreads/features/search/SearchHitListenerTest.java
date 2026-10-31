package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;

@ExtendWith(OutputCaptureExtension.class)
class SearchHitListenerTest {

    private static final String BOOK_ID = BookSearchDocuments.EYE_OF_THE_WORLD_ID;

    private static final String MATCHING_QUERY = "eye of the world";

    private static final String OTHER_QUERY = "dune";

    private final BookSearchService searchService = Mockito.mock(BookSearchService.class);

    private final SearchHitEmitters emitters = Mockito.mock(SearchHitEmitters.class);

    private final SearchHitListener listener = new SearchHitListener(searchService, emitters);

    @Test
    void shouldPushHitOnlyToQueriesTheBookMatches() {
        final BookSearchDocument hit = BookSearchDocuments.eyeOfTheWorld();
        when(emitters.openQueries()).thenReturn(Set.of(MATCHING_QUERY, OTHER_QUERY));
        when(searchService.hitFor(MATCHING_QUERY, BOOK_ID)).thenReturn(Optional.of(hit));
        when(searchService.hitFor(OTHER_QUERY, BOOK_ID)).thenReturn(Optional.empty());

        listener.onBookIndexed(new BookIndexedEvent(BOOK_ID));

        verify(emitters).publish(MATCHING_QUERY, hit);
        verify(emitters, never()).publish(eq(OTHER_QUERY), any());
    }

    @Test
    void shouldPushHitToOtherQueriesWhenHitLookupFails() {
        final BookSearchDocument hit = BookSearchDocuments.eyeOfTheWorld();
        when(emitters.openQueries()).thenReturn(Set.of(MATCHING_QUERY, OTHER_QUERY));
        when(searchService.hitFor(MATCHING_QUERY, BOOK_ID)).thenReturn(Optional.of(hit));
        when(searchService.hitFor(OTHER_QUERY, BOOK_ID)).thenThrow(badDocument());

        listener.onBookIndexed(new BookIndexedEvent(BOOK_ID));

        verify(emitters).publish(MATCHING_QUERY, hit);
    }

    @Test
    void shouldPushHitToOtherQueriesWhenPublishFails() {
        final BookSearchDocument hit = BookSearchDocuments.eyeOfTheWorld();
        when(emitters.openQueries()).thenReturn(Set.of(MATCHING_QUERY, OTHER_QUERY));
        when(searchService.hitFor(MATCHING_QUERY, BOOK_ID)).thenReturn(Optional.of(hit));
        when(searchService.hitFor(OTHER_QUERY, BOOK_ID)).thenReturn(Optional.of(hit));
        doThrow(new IllegalStateException("async request already completed"))
            .when(emitters).publish(OTHER_QUERY, hit);

        listener.onBookIndexed(new BookIndexedEvent(BOOK_ID));

        verify(emitters).publish(MATCHING_QUERY, hit);
    }

    @Test
    void shouldLogWarnWhenQueryFails(final CapturedOutput output) {
        when(emitters.openQueries()).thenReturn(Set.of(OTHER_QUERY));
        when(searchService.hitFor(OTHER_QUERY, BOOK_ID)).thenThrow(badDocument());

        listener.onBookIndexed(new BookIndexedEvent(BOOK_ID));

        assertThat(output).contains("search.hit-push failed");
    }

    @Test
    void shouldPropagateUnlistedFailure() {
        final BookIndexedEvent event = new BookIndexedEvent(BOOK_ID);
        when(emitters.openQueries()).thenReturn(Set.of(OTHER_QUERY));
        when(searchService.hitFor(OTHER_QUERY, BOOK_ID)).thenThrow(new UnsupportedOperationException("a bug"));

        assertThrows(UnsupportedOperationException.class, () -> listener.onBookIndexed(event));
    }

    private static DatabindException badDocument() {
        return DatabindException.from((JsonParser) null, "bad document");
    }
}
