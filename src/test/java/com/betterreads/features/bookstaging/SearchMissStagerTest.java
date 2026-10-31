package com.betterreads.features.bookstaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.betterreads.bookdiscovery.BookDiscovery;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
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

class SearchMissStagerTest {

    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(10);

    private static final Executor SAME_THREAD = Runnable::run;

    private static final String DUNE = "dune";

    private static final String FOUNDATION = "foundation";

    private final BookDiscovery bookDiscovery = Mockito.mock(BookDiscovery.class);

    private final SearchMissStager stager = new SearchMissStager(bookDiscovery, SAME_THREAD, DEDUP_WINDOW);

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
    @DisplayName("should drop a repeat while the run is in flight")
    void shouldDropRepeatWhileRunIsInFlight() {
        final List<Runnable> queued = new ArrayList<>();
        final SearchMissStager queuedStager = new SearchMissStager(bookDiscovery, queued::add, DEDUP_WINDOW);

        queuedStager.stage(DUNE);
        queuedStager.stage(DUNE);

        assertThat(queued).hasSize(1);
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

        private static final String SOURCE_URL = "https://source.example/search";

        private static final String BAD_BODY = "bad body";

        static Stream<RuntimeException> failures() {
            return Stream.of(
                StubSourceClients.serviceUnavailable(),
                StubSourceClients.rateLimited(),
                new QueryTimeoutException("database timed out"),
                new DecodingException(BAD_BODY),
                new RejectedExecutionException("source pool full"),
                new WebClientRequestException(
                    new ConnectException("Connection refused"), HttpMethod.GET, URI.create(SOURCE_URL),
                    HttpHeaders.EMPTY));
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
        @DisplayName("should swallow a decode failure")
        void shouldSwallowDecodeFailure() {
            doThrow(new DecodingException(BAD_BODY)).when(bookDiscovery).searchAndStage(DUNE);

            assertThatCode(() -> stager.stage(DUNE)).doesNotThrowAnyException();
        }
    }
}
