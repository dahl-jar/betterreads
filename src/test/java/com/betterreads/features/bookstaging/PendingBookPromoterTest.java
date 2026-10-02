package com.betterreads.features.bookstaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.betterreads.book.BookUpsertService;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookMapper;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.pendingbook.PendingBookStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class PendingBookPromoterTest {

    private static final String DEDUP_KEY = DuneBooks.ISBN;

    private static final String TITLE = DuneBooks.TITLE;

    private static final String PUBLISHER = "Chilton Books";

    private final PendingBookRepository pendingBooks = mock(PendingBookRepository.class);

    private final PendingBookPromoter promoter = new PendingBookPromoter(
        pendingBooks, mock(BookUpsertService.class), new RequiredFieldsCheck(),
        new PendingBookMapper(), mock(ApplicationEventPublisher.class));

    @Test
    @DisplayName("an incomplete attempt is recorded and stays PENDING below the cap")
    void incompleteAttemptIsRecorded() {
        final PendingBook row = pendingRow(0);
        when(pendingBooks.findByDedupKey(DEDUP_KEY)).thenReturn(Optional.of(row));

        promoter.promote(DEDUP_KEY, incompleteDune());

        assertOneAttemptStillPending(row);
    }

    @Test
    @DisplayName("the attempt that reaches the cap retires the candidate")
    void attemptReachingCapRetires() {
        final PendingBook row = pendingRow(PendingBookPromoter.MAX_ATTEMPTS - 1);
        when(pendingBooks.findByDedupKey(DEDUP_KEY)).thenReturn(Optional.of(row));

        promoter.promote(DEDUP_KEY, incompleteDune());

        assertThat(row.getStatus()).isEqualTo(PendingBookStatus.INCOMPLETE_FINAL);
    }

    @Test
    @DisplayName("an incomplete attempt stores the collected fields and what is still missing")
    void shouldStoreCollectedFieldsOnIncompleteAttempt() {
        final PendingBook row = pendingRow(0);
        when(pendingBooks.findByDedupKey(DEDUP_KEY)).thenReturn(Optional.of(row));
        final SourceBook collected = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
            .isbn13(DEDUP_KEY)
            .title(TITLE)
            .publisher(PUBLISHER)
            .build();

        promoter.promote(DEDUP_KEY, new SourceMerger().merge(null, List.of(collected)));

        assertThat(row).satisfies(attempted -> {
            assertThat(attempted.getPublisher()).isEqualTo(PUBLISHER);
            assertThat(attempted.getMissingFields()).isEqualTo("author\ncover\ndescription\nyear");
        });
    }

    @Test
    @DisplayName("a failed promotion records the attempt and stays PENDING below the cap")
    void failedPromotionRecordsAttempt() {
        final PendingBook row = pendingRow(0);
        when(pendingBooks.findByDedupKey(DEDUP_KEY)).thenReturn(Optional.of(row));

        promoter.recordFailedAttempt(DEDUP_KEY);

        assertOneAttemptStillPending(row);
    }

    private static void assertOneAttemptStillPending(final PendingBook row) {
        assertThat(row).satisfies(attempted -> {
            assertThat(attempted.getAttemptCount()).isEqualTo(1);
            assertThat(attempted.getLastAttemptAt()).isNotNull();
            assertThat(attempted.getStatus()).isEqualTo(PendingBookStatus.PENDING);
        });
    }

    private static PendingBook pendingRow(final int attemptCount) {
        final PendingBook row = DuneBooks.pendingRow(DEDUP_KEY, TITLE);
        row.setStatus(PendingBookStatus.PENDING);
        row.setAttemptCount(attemptCount);
        return row;
    }

    private static MergedBook incompleteDune() {
        final SourceBook sparse = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(DEDUP_KEY)
            .title(TITLE)
            .build();
        return new SourceMerger().merge(null, List.of(sparse));
    }
}
