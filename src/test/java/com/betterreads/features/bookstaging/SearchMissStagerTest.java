package com.betterreads.features.bookstaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betterreads.bookdiscovery.BookDiscovery;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcoverauthor.HardcoverAuthorClient;
import com.betterreads.clients.hardcoverseries.HardcoverSeriesClient;
import com.betterreads.clients.openlibrary.OpenLibraryClient;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.core.codec.DecodingException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class SearchMissStagerTest {

    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(10);

    private static final String SOURCE_URL = "https://source.example/search";

    private static final Executor SAME_THREAD = Runnable::run;

    private static final String DUNE = "dune";

    private static final String FOUNDATION = "foundation";

    private final BookDiscovery bookDiscovery = Mockito.mock(BookDiscovery.class);

    private final SearchMissStager stager = new SearchMissStager(bookDiscovery, SAME_THREAD, DEDUP_WINDOW);

    @Test
    @DisplayName("stages a fresh query through the catalog search once")
    void stagesFreshQuery() {
        final String query = "wheel of time";

        stager.stage(query);

        verify(bookDiscovery).searchAndStage(query);
    }

    @Test
    @DisplayName("drops a repeat of the same query within the dedup window")
    void dropsRepeatQuery() {
        stager.stage(DUNE);
        stager.stage(DUNE);

        verify(bookDiscovery, times(1)).searchAndStage(DUNE);
    }

    @Test
    @DisplayName("treats queries differing only by case and surrounding space as the same")
    void normalizesBeforeDedup() {
        stager.stage("Mistborn");
        stager.stage("  mistborn ");

        verify(bookDiscovery, times(1)).searchAndStage(Mockito.anyString());
    }

    @Test
    @DisplayName("swallows a staging failure so the caller is never affected")
    void swallowsStagingFailure() {
        doThrow(new QueryTimeoutException("source timed out")).when(bookDiscovery).searchAndStage(DUNE);

        assertThatCode(() -> stager.stage(DUNE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should swallow a refused connection to a source")
    void shouldSwallowRefusedConnection() {
        final WebClientRequestException refused = new WebClientRequestException(
            new ConnectException("Connection refused"), HttpMethod.GET, URI.create(SOURCE_URL), HttpHeaders.EMPTY);
        doThrow(refused).when(bookDiscovery).searchAndStage(DUNE);

        assertThatCode(() -> stager.stage(DUNE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("retries a query whose first submission the executor rejected")
    void retriesAfterRejection() {
        final AtomicInteger submissions = new AtomicInteger();
        final AtomicBoolean reject = new AtomicBoolean(true);
        final Executor flaky = task -> {
            submissions.incrementAndGet();
            if (reject.get()) {
                throw new RejectedExecutionException("pool full");
            }
            task.run();
        };
        final SearchMissStager flakyStager = new SearchMissStager(bookDiscovery, flaky, DEDUP_WINDOW);

        flakyStager.stage(DUNE);
        reject.set(false);
        flakyStager.stage(DUNE);

        assertThat(submissions.get()).isEqualTo(2);
        verify(bookDiscovery, times(1)).searchAndStage(DUNE);
    }

    @Test
    @DisplayName("stages distinct queries independently")
    void stagesDistinctQueries() {
        stager.stage(DUNE);
        stager.stage(FOUNDATION);

        verify(bookDiscovery).searchAndStage(DUNE);
        verify(bookDiscovery).searchAndStage(FOUNDATION);
    }

    @Nested
    @DisplayName("after a staging run ends")
    class RunEnd {

        private static final int HTTP_TOO_MANY_REQUESTS = 429;

        private static final String BAD_BODY = "bad body";

        private final HardcoverSeriesClient seriesClient = Mockito.mock(HardcoverSeriesClient.class);

        private final HardcoverAuthorClient authorClient = Mockito.mock(HardcoverAuthorClient.class);

        private final OpenLibraryClient openLibraryClient = Mockito.mock(OpenLibraryClient.class);

        private final BookStager bookStager = Mockito.mock(BookStager.class);

        private final SearchMissStager discoveryStager = new SearchMissStager(
            new BookDiscoveryService(seriesClient, authorClient, openLibraryClient, bookStager),
            SAME_THREAD,
            DEDUP_WINDOW);

        static Stream<RuntimeException> failures() {
            return Stream.of(
                StubSourceClients.serviceUnavailable(),
                WebClientResponseException.create(
                    HTTP_TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null),
                new QueryTimeoutException("database timed out"),
                new DecodingException(BAD_BODY),
                new RejectedExecutionException("source pool full"));
        }

        @ParameterizedTest
        @MethodSource("failures")
        @DisplayName("should stage again when the run fails")
        void shouldStageAgainWhenRunFails(final RuntimeException failure) {
            doThrow(failure).when(bookDiscovery).searchAndStage(DUNE);

            stager.stage(DUNE);
            stager.stage(DUNE);

            verify(bookDiscovery, times(2)).searchAndStage(DUNE);
        }

        @Test
        @DisplayName("should not throw to the caller when the run fails")
        void shouldNotThrowToCallerWhenRunFails() {
            doThrow(new DecodingException(BAD_BODY)).when(bookDiscovery).searchAndStage(DUNE);

            assertThatCode(() -> stager.stage(DUNE)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should stay blocked after a clean miss")
        void shouldStayBlockedAfterCleanMiss() {
            discoveryStager.stage(DUNE);
            discoveryStager.stage(DUNE);

            verify(seriesClient, times(1)).fetchSeries(DUNE);
        }

        @Test
        @DisplayName("should stay blocked after staging books")
        void shouldStayBlockedAfterStagingBooks() {
            final SourceBook.Builder builder = SourceBook.builder(BookFieldSource.OPEN_LIBRARY);
            builder.title(DUNE);
            final SourceBook hit = builder.build();
            when(openLibraryClient.search(Mockito.eq(DUNE), Mockito.anyInt())).thenReturn(List.of(hit));

            discoveryStager.stage(DUNE);
            discoveryStager.stage(DUNE);

            verify(bookStager, times(1)).stage(hit);
        }
    }
}
