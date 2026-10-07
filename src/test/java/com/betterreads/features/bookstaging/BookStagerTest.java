package com.betterreads.features.bookstaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(OutputCaptureExtension.class)
class BookStagerTest {

    private static final String DUPLICATE = "catalog.search candidate duplicates an existing row";

    private static final String FAILED = "catalog.search staging failed";

    private final SourceCollector sourceCollector = mock(SourceCollector.class);

    private final PendingBookService pendingBookService = mock(PendingBookService.class);

    private final BookStager stager =
        new BookStager(sourceCollector, pendingBookService, mock(PendingBookPromoter.class));

    private final SourceBook seed = DuneBooks.openLibraryDune();

    @BeforeEach
    void setUp() {
        final MergedBook merged = new SourceMerger().merge(null, List.of(seed));
        when(sourceCollector.collectFor(seed)).thenReturn(merged);
    }

    @Test
    void shouldLogADuplicateStageAtInfo(final CapturedOutput output) {
        doThrow(new DataIntegrityViolationException("duplicate key")).when(pendingBookService).stage(any());

        stager.stage(seed);

        assertThat(output.getOut().lines())
            .filteredOn(line -> line.contains(DUPLICATE))
            .singleElement()
            .asString()
            .contains(" INFO ");
    }

    @Test
    void shouldWarnWhenStagingFailsForAnotherReason(final CapturedOutput output) {
        doThrow(new QueryTimeoutException("statement timeout")).when(pendingBookService).stage(any());

        stager.stage(seed);

        assertThat(output.getOut().lines())
            .filteredOn(line -> line.contains(FAILED) || line.contains(DUPLICATE))
            .singleElement()
            .asString()
            .contains(" WARN ", FAILED);
    }
}
