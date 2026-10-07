package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betterreads.bookindex.BookIndexViewReader;
import com.betterreads.clients.meilisearch.MeilisearchProperties;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.DocumentsQuery;
import com.meilisearch.sdk.model.SearchResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class MeilisearchBookSearchServiceTest {

    private static final String INDEX = "books";

    private static final String QUERY = "edgedancer";

    private static final long BOOK_ID = 7L;

    private static final String DEDUP_KEY = "9780765386564";

    private static final int FULL_PAGE = 20;

    private static final String NO_DOCUMENTS = "{\"results\": []}";

    private static final String INDEX_DOWN = "index down";

    private final Client client = Mockito.mock(Client.class);

    private final Index index = Mockito.mock(Index.class);

    private final SearchResult empty = Mockito.mock(SearchResult.class);

    private final CatalogFallback fallback = Mockito.mock(CatalogFallback.class);

    private final BookIndexViewReader indexViews = Mockito.mock(BookIndexViewReader.class);

    private final MeilisearchBookSearchService service = new MeilisearchBookSearchService(
        client, new MeilisearchProperties("http://meili.example.test", "key", INDEX, "authors"),
        new JsonMapper(), fallback, indexViews, new BookSearchDocumentMapper());

    @BeforeEach
    void setUp() {
        when(client.index(INDEX)).thenReturn(index);
        when(empty.getHits()).thenReturn(new ArrayList<>());
        when(index.getRawDocuments(any(DocumentsQuery.class))).thenReturn(NO_DOCUMENTS);
        when(fallback.find(QUERY)).thenReturn(List.of(BOOK_ID));
        when(indexViews.indexViewsByIds(List.of(BOOK_ID)))
            .thenReturn(List.of(BookIndexViews.titled(DEDUP_KEY, "Edgedancer")));
    }

    @Test
    void shouldReturnFallbackBooksWhenTheIndexCheckFails() {
        when(index.search(any(SearchRequest.class))).thenReturn(empty);
        when(index.getRawDocuments(any(DocumentsQuery.class))).thenThrow(new MeilisearchException(INDEX_DOWN));

        final SearchOutcome outcome = service.search(QUERY, 0, FULL_PAGE);

        assertThat(outcome.result().hits()).extracting(BookSearchDocument::bookId).containsExactly(DEDUP_KEY);
    }

    @Test
    void shouldReturnFallbackBooksWhenIndexingThemFails() {
        when(index.search(any(SearchRequest.class))).thenReturn(empty);
        when(index.addDocuments(anyString(), anyString())).thenThrow(new MeilisearchException(INDEX_DOWN));

        final SearchOutcome outcome = service.search(QUERY, 0, FULL_PAGE);

        assertThat(outcome.result().hits()).extracting(BookSearchDocument::bookId).containsExactly(DEDUP_KEY);
    }

    @Test
    void shouldNotIndexFallbackBooksWhenTheSearchFails() {
        when(index.search(any(SearchRequest.class))).thenThrow(new MeilisearchException("search down"));

        service.search(QUERY, 0, FULL_PAGE);

        verify(index, never()).addDocuments(anyString(), anyString());
    }

    @Test
    void shouldNotReindexAFallbackBookTheIndexHolds() {
        when(index.search(any(SearchRequest.class))).thenReturn(empty);
        when(index.getRawDocuments(any(DocumentsQuery.class)))
            .thenReturn("{\"results\": [{\"bookId\": \"" + DEDUP_KEY + "\"}]}");

        service.search(QUERY, 0, FULL_PAGE);

        verify(index, never()).addDocuments(anyString(), anyString());
    }
}
