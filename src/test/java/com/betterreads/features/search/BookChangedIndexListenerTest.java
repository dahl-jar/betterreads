package com.betterreads.features.search;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.betterreads.book.BookChangedEvent;
import com.betterreads.book.BookDetailCache;
import com.betterreads.bookindex.BookIndexView;
import com.betterreads.bookindex.BookIndexViewReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

class BookChangedIndexListenerTest {

    private static final long BOOK_ID = 7L;

    private static final String DEDUP_KEY = "9780000000001";

    private final BookIndexViewReader indexViews = Mockito.mock(BookIndexViewReader.class);

    private final BookSearchService searchService = Mockito.mock(BookSearchService.class);

    private final ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);

    private final CacheManager cacheManager = Mockito.mock(CacheManager.class);

    private final Cache cache = Mockito.mock(Cache.class);

    private final BookChangedIndexListener listener = new BookChangedIndexListener(
        indexViews, new BookSearchDocumentMapper(), searchService, events, cacheManager);

    @BeforeEach
    void setUp() {
        when(cacheManager.getCache(BookDetailCache.NAME)).thenReturn(cache);
    }

    @Test
    void shouldAnnounceBookAfterIndexing() {
        when(indexViews.indexViewsByIds(List.of(BOOK_ID))).thenReturn(List.of(indexView()));

        listener.onBookChanged(new BookChangedEvent(BOOK_ID));

        verify(events).publishEvent(new BookIndexedEvent(DEDUP_KEY));
    }

    @Test
    void shouldNotAnnounceWhenIndexingFails() {
        failIndexing();

        listener.onBookChanged(new BookChangedEvent(BOOK_ID));

        verifyNoInteractions(events);
    }

    @Test
    void shouldEvictTheDetailWhenIndexingFails() {
        failIndexing();

        listener.onBookChanged(new BookChangedEvent(BOOK_ID));

        verify(cache).evict(DEDUP_KEY);
    }

    private void failIndexing() {
        when(indexViews.indexViewsByIds(List.of(BOOK_ID))).thenReturn(List.of(indexView()));
        doThrow(new SearchIndexException("indexing failed", new IllegalStateException()))
            .when(searchService).index(anyCollection());
    }

    @Test
    void shouldSkipIndexingWhenBookIsGone() {
        when(indexViews.indexViewsByIds(List.of(BOOK_ID))).thenReturn(List.of());

        listener.onBookChanged(new BookChangedEvent(BOOK_ID));

        verifyNoInteractions(searchService, events, cache);
    }

    private static BookIndexView indexView() {
        return new BookIndexView(DEDUP_KEY, "The Eye of the World", null, "The Wheel of Time", 1.0, List.of(),
            List.of("Robert Jordan"), List.of(), "en", null, null, null, null);
    }
}
