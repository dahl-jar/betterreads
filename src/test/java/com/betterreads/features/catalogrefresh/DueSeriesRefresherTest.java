package com.betterreads.features.catalogrefresh;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.betterreads.bookdiscovery.SeriesRefresh;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class DueSeriesRefresherTest {

    private static final String WHEEL_OF_TIME = "The Wheel of Time";

    private static final String STORMLIGHT = "The Stormlight Archive";

    private static final String MISTBORN = "Mistborn";

    private static final int HTTP_BAD_GATEWAY = 502;

    private static final int HALF_THE_LIMIT = CatalogRefreshSamples.MAX_BOOKS / 2;

    private final SeriesRefreshRepository refreshTimes = mock(SeriesRefreshRepository.class);

    private final SeriesRefresh seriesRefresh = mock(SeriesRefresh.class);

    private final DueSeriesRefresher refresher =
        new DueSeriesRefresher(refreshTimes, seriesRefresh, CatalogRefreshSamples.properties(false, true));

    @Test
    void shouldStopTakingSeriesOnceTheBookLimitIsReached() {
        when(refreshTimes.findSeriesDueForRefresh()).thenReturn(List.of(WHEEL_OF_TIME, STORMLIGHT, MISTBORN));
        when(seriesRefresh.refresh(anyString())).thenReturn(HALF_THE_LIMIT);

        refresher.refresh();

        verify(seriesRefresh).refresh(WHEEL_OF_TIME);
        verify(seriesRefresh).refresh(STORMLIGHT);
        verify(seriesRefresh, never()).refresh(MISTBORN);
    }

    @Test
    void shouldMarkAResolvedSeriesRefreshed() {
        when(refreshTimes.findSeriesDueForRefresh()).thenReturn(List.of(WHEEL_OF_TIME));

        refresher.refresh();

        verify(refreshTimes).markRefreshed(eq(WHEEL_OF_TIME), any(OffsetDateTime.class));
    }

    static Stream<RuntimeException> discoveryFailures() {
        return Stream.of(
            WebClientResponseException.create(HTTP_BAD_GATEWAY, "Bad Gateway", HttpHeaders.EMPTY, new byte[0], null),
            new DataAccessResourceFailureException("connection lost"));
    }

    @ParameterizedTest
    @MethodSource("discoveryFailures")
    void shouldMarkASeriesRefreshedAfterAFailedDiscovery(final RuntimeException failure) {
        when(refreshTimes.findSeriesDueForRefresh()).thenReturn(List.of(WHEEL_OF_TIME, STORMLIGHT));
        when(seriesRefresh.refresh(WHEEL_OF_TIME)).thenThrow(failure);

        refresher.refresh();

        verify(refreshTimes).markRefreshed(eq(WHEEL_OF_TIME), any(OffsetDateTime.class));
        verify(seriesRefresh).refresh(STORMLIGHT);
    }
}
