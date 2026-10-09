package com.betterreads.features.bookstaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.betterreads.book.BookRepository;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceSeries;
import com.betterreads.booksource.SourceSeriesVolume;
import com.betterreads.clients.hardcoverseries.HardcoverSeriesClient;
import org.junit.jupiter.api.Test;

class SeriesRefreshServiceTest {

    private static final String WHEEL_OF_TIME = "The Wheel of Time";

    private static final String EYE_ID = "hc-eye";

    private static final String GREAT_HUNT_ID = "hc-great-hunt";

    private final HardcoverSeriesClient seriesClient = mock(HardcoverSeriesClient.class);

    private final BookRepository books = mock(BookRepository.class);

    private final BookStager stager = mock(BookStager.class);

    private final SeriesRefreshService service = new SeriesRefreshService(seriesClient, books, stager);

    private static SourceBook volume(final String title, final String hardcoverId) {
        return SourceBook.builder(BookFieldSource.HARDCOVER).hardcoverId(hardcoverId).title(title).build();
    }

    @Test
    void shouldSkipAVolumeWhoseBookHasAVerifiedSeries() {
        final SourceBook eye = volume("The Eye of the World", EYE_ID);
        final SourceBook greatHunt = volume("The Great Hunt", GREAT_HUNT_ID);
        final SourceSeries series = new SourceSeries(WHEEL_OF_TIME, "Robert Jordan",
            List.of(new SourceSeriesVolume(1, eye), new SourceSeriesVolume(2, greatHunt)), null);
        when(seriesClient.fetchSeries(WHEEL_OF_TIME)).thenReturn(Optional.of(series));
        when(books.existsSeriesVerifiedByHardcoverId(EYE_ID)).thenReturn(true);

        final int collected = service.refresh(WHEEL_OF_TIME);

        assertThat(collected).isEqualTo(1);
        verify(stager).stage(greatHunt);
        verify(stager, never()).stage(eye);
    }
}
