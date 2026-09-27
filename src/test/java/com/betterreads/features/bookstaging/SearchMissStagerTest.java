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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;

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
}
