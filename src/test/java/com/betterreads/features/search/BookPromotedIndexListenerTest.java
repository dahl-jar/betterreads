package com.betterreads.features.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betterreads.book.BookPromotedEvent;
import com.betterreads.bookindex.BookIndexView;
import com.betterreads.bookindex.BookIndexViewReader;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class BookPromotedIndexListenerTest {

    private static final String BOOK_ID = "9780000000001";

    private final BookIndexViewReader indexViews = Mockito.mock(BookIndexViewReader.class);

    private final BookSearchService searchService = Mockito.mock(BookSearchService.class);

    private final ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);

    private final BookPromotedIndexListener listener = new BookPromotedIndexListener(
        indexViews, new BookSearchDocumentMapper(), searchService, events);

    @Test
    void shouldAnnounceBookAfterIndexing() {
        when(indexViews.indexViewByKey(BOOK_ID)).thenReturn(Optional.of(indexView()));

        listener.onBookPromoted(new BookPromotedEvent(BOOK_ID));

        verify(events).publishEvent(new BookIndexedEvent(BOOK_ID));
    }

    @Test
    void shouldNotPublishWhenIndexingFails() {
        when(indexViews.indexViewByKey(BOOK_ID)).thenReturn(Optional.of(indexView()));
        doThrow(new SearchIndexException("indexing failed", new IllegalStateException()))
            .when(searchService).index(anyCollection());

        Assertions.assertThatThrownBy(() -> listener.onBookPromoted(new BookPromotedEvent(BOOK_ID)))
            .isInstanceOf(SearchIndexException.class);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void shouldSkipIndexingWhenPromotedBookIsGone() {
        when(indexViews.indexViewByKey(BOOK_ID)).thenReturn(Optional.empty());

        listener.onBookPromoted(new BookPromotedEvent(BOOK_ID));

        verify(searchService, never()).index(anyCollection());
        verify(events, never()).publishEvent(any());
    }

    private static BookIndexView indexView() {
        return new BookIndexView(BOOK_ID, "The Eye of the World", null, "The Wheel of Time", 1,
            List.of("Robert Jordan"), List.of(), "en", null, null, null, null);
    }
}
