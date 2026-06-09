package com.betterreads.catalog.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.mapper.PendingBookMapper;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.read.CatalogService;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.source.SourceMerger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** An incomplete candidate's failed attempt is recorded, and the attempt cap retires it. */
class PendingBookPromoterTest {

    private static final String DEDUP_KEY = "9780441013593";

    private static final String TITLE = "Dune";

    private static final String STATUS_PENDING = "PENDING";

    private final PendingBookRepository pendingBooks = mock(PendingBookRepository.class);

    private final PendingBookPromoter promoter = new PendingBookPromoter(
        pendingBooks, mock(CatalogService.class), new RequiredFieldsCheck(),
        new PendingBookMapper(), mock(ApplicationEventPublisher.class));

    @Test
    @DisplayName("an incomplete attempt is recorded and stays PENDING below the cap")
    void incompleteAttemptIsRecorded() {
        final PendingBook row = pendingRow(0);
        when(pendingBooks.findByDedupKey(DEDUP_KEY)).thenReturn(Optional.of(row));

        promoter.promote(DEDUP_KEY, incompleteDune());

        assertThat(row).satisfies(attempted -> {
            assertThat(attempted.getAttemptCount()).isEqualTo(1);
            assertThat(attempted.getLastAttemptAt()).isNotNull();
            assertThat(attempted.getStatus()).isEqualTo(STATUS_PENDING);
        });
    }

    @Test
    @DisplayName("the attempt that reaches the cap retires the candidate")
    void attemptReachingCapRetires() {
        final PendingBook row = pendingRow(PendingBookPromoter.MAX_ATTEMPTS - 1);
        when(pendingBooks.findByDedupKey(DEDUP_KEY)).thenReturn(Optional.of(row));

        promoter.promote(DEDUP_KEY, incompleteDune());

        assertThat(row.getStatus()).isEqualTo("INCOMPLETE_FINAL");
    }

    private static PendingBook pendingRow(final int attemptCount) {
        final PendingBook row = new PendingBook();
        row.setDedupKey(DEDUP_KEY);
        row.setIsbn13(DEDUP_KEY);
        row.setTitle(TITLE);
        row.setStatus(STATUS_PENDING);
        row.setAttemptCount(attemptCount);
        return row;
    }

    private static MergedBook incompleteDune() {
        final SourceBook sparse = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(DEDUP_KEY)
            .title(TITLE)
            .build();
        return new SourceMerger().merge(List.of(sparse));
    }
}
