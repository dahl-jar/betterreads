package com.betterreads.features.bookstaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.betterreads.bookdescription.DescriptionSelector;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookMapper;
import com.betterreads.pendingbook.PendingBookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class PendingBookServiceTest {

    private static final String DUNE_KEY = DuneBooks.ISBN;

    private static final String MESSIAH_KEY = DuneBooks.SEQUEL_ISBN;

    private static final String DUNE_TITLE = DuneBooks.TITLE;

    private static final String MESSIAH_TITLE = DuneBooks.SEQUEL_TITLE;

    private static final String STAGED_RATING = "4.25";

    private static final int STAGED_RATING_COUNT = 950;

    private final PendingBookRepository pendingBooks = mock(PendingBookRepository.class);

    private final PendingBookPromoter promoter = mock(PendingBookPromoter.class);

    private final PendingBookService service = new PendingBookService(
        pendingBooks, new PendingBookMapper(), new SourceCollector(
            new SourceMerger(), List.of(), new DescriptionSelector(List.of()), Runnable::run),
        promoter);

    @Test
    @DisplayName("staging a book carrying no source identifier is rejected before any row is reserved")
    void stagingWithoutASourceIdentifierIsRejected() {
        final SourceBook keyless = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .title(DUNE_TITLE)
            .build();

        assertThatThrownBy(() -> service.stage(new SourceMerger().merge(null, List.of(keyless))))
            .isInstanceOf(IllegalArgumentException.class);
        verify(pendingBooks, never()).reserve(anyString());
    }

    @Test
    @DisplayName("a non-integrity promotion failure counts as an attempt")
    void failureRecordsAttemptAndPollContinues() {
        final PendingBook dune = DuneBooks.pendingRow(DUNE_KEY, DUNE_TITLE);
        final PendingBook messiah = DuneBooks.pendingRow(MESSIAH_KEY, MESSIAH_TITLE);
        when(pendingBooks.findDue(any())).thenReturn(List.of(dune, messiah));
        when(pendingBooks.findByDedupKey(DUNE_KEY)).thenReturn(Optional.of(dune));
        when(pendingBooks.findByDedupKey(MESSIAH_KEY)).thenReturn(Optional.of(messiah));
        doThrow(new DataAccessResourceFailureException("connection reset"))
            .when(promoter).promote(eq(DUNE_KEY), any());

        service.promoteReady();

        verify(promoter).recordFailedAttempt(DUNE_KEY);
        verify(promoter).promote(eq(MESSIAH_KEY), any());
        verify(promoter, never()).markDuplicate(DUNE_KEY);
    }

    @Test
    @DisplayName("a re-promotion with no Hardcover match keeps the staged rating")
    void shouldKeepStagedRatingWhenCollectFindsNone() {
        final PendingBook staged = DuneBooks.pendingRow(DUNE_KEY, DUNE_TITLE);
        staged.setAverageRating(new BigDecimal(STAGED_RATING));
        staged.setRatingCount(STAGED_RATING_COUNT);
        when(pendingBooks.findDue(any())).thenReturn(List.of(staged));
        when(pendingBooks.findByDedupKey(DUNE_KEY)).thenReturn(Optional.of(staged));
        final ArgumentCaptor<MergedBook> promoted = ArgumentCaptor.forClass(MergedBook.class);

        service.promoteReady();

        verify(promoter).promote(eq(DUNE_KEY), promoted.capture());
        final SourceBook book = promoted.getValue().book();
        assertThat(book.averageRating()).isEqualTo(Double.parseDouble(STAGED_RATING));
        assertThat(book.ratingCount()).isEqualTo(STAGED_RATING_COUNT);
    }
}
