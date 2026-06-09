package com.betterreads.catalog.service.pipeline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.mapper.PendingBookMapper;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.source.SourceMerger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** The promotion poll isolates one candidate's failure from the rest of the queue. */
class PendingBookServiceTest {

    private static final String FAILING_KEY = "9780441013593";

    private static final String NEXT_KEY = "9780441172696";

    private static final String FAILING_TITLE = "Dune";

    private static final String NEXT_TITLE = "Dune Messiah";

    private final PendingBookRepository pendingBooks = mock(PendingBookRepository.class);

    private final PendingBookPromoter promoter = mock(PendingBookPromoter.class);

    private final PendingBookService service = new PendingBookService(
        pendingBooks, new PendingBookMapper(), new SourceCollector(
            new SourceMerger(), List.of(), new DescriptionSelector(List.of()), Runnable::run),
        promoter);

    @Test
    @DisplayName("a transient promotion failure leaves the candidate PENDING and the poll continues")
    void transientFailureRetriesLaterAndPollContinues() {
        when(pendingBooks.findPendingNotAttemptedSince(any()))
            .thenReturn(List.of(row(FAILING_KEY, FAILING_TITLE), row(NEXT_KEY, NEXT_TITLE)));
        when(pendingBooks.findByDedupKey(FAILING_KEY)).thenReturn(Optional.of(row(FAILING_KEY, FAILING_TITLE)));
        when(pendingBooks.findByDedupKey(NEXT_KEY)).thenReturn(Optional.of(row(NEXT_KEY, NEXT_TITLE)));
        doThrow(new DataAccessResourceFailureException("connection reset"))
            .when(promoter).promote(eq(FAILING_KEY), any());

        service.promoteReady();

        verify(promoter).promote(eq(NEXT_KEY), any());
        verify(promoter, never()).markDuplicate(FAILING_KEY);
    }

    private static PendingBook row(final String dedupKey, final String title) {
        final PendingBook row = new PendingBook();
        row.setDedupKey(dedupKey);
        row.setIsbn13(dedupKey);
        row.setTitle(title);
        return row;
    }
}
