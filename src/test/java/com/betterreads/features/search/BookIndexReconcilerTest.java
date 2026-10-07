package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.betterreads.bookindex.BookIndexViewReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class BookIndexReconcilerTest {

    private static final int PAGE_SIZE = 10;

    private final BookSearchService searchService = Mockito.mock(BookSearchService.class);

    private final BookIndexReconciler reconciler = new BookIndexReconciler(
        Mockito.mock(BookIndexViewReader.class), new BookSearchDocumentMapper(), searchService,
        Mockito.mock(BookSearchRepository.class), PAGE_SIZE);

    @Test
    void shouldLogTheFullPassFailureWhenTheIndexIsDown(final CapturedOutput output) {
        when(searchService.indexedIds(anyInt()))
            .thenThrow(new SearchIndexException("index down", new IllegalStateException()));

        reconciler.reconcile();

        assertThat(output).contains("search.reconcile-full-failed");
    }
}
