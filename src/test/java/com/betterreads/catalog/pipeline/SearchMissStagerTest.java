package com.betterreads.catalog.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.betterreads.catalog.service.pipeline.CatalogSearchService;
import com.betterreads.catalog.service.pipeline.SearchMissStager;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Verifies the search-miss stager delegates each fresh query to staging once and drops a repeat of
 * the same query inside the dedup window.
 */
class SearchMissStagerTest {

    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(10);

    private static final int HTTP_BAD_GATEWAY = 502;

    private static final Executor SAME_THREAD = Runnable::run;

    private static final String DUNE = "dune";

    private static final String FOUNDATION = "foundation";

    private final CatalogSearchService catalogSearch = Mockito.mock(CatalogSearchService.class);

    private final SearchMissStager stager = new SearchMissStager(catalogSearch, SAME_THREAD, DEDUP_WINDOW);

    @Test
    @DisplayName("stages a fresh query through the catalog search once")
    void stagesFreshQuery() {
        final String query = "wheel of time";

        stager.stage(query);

        verify(catalogSearch).searchAndStage(query);
    }

    @Test
    @DisplayName("drops a repeat of the same query within the dedup window")
    void dropsRepeatQuery() {
        stager.stage(DUNE);
        stager.stage(DUNE);

        verify(catalogSearch, times(1)).searchAndStage(DUNE);
    }

    @Test
    @DisplayName("treats queries differing only by case and surrounding space as the same")
    void normalizesBeforeDedup() {
        stager.stage("Mistborn");
        stager.stage("  mistborn ");

        verify(catalogSearch, times(1)).searchAndStage(Mockito.anyString());
    }

    @Test
    @DisplayName("swallows a staging failure so the caller is never affected")
    void swallowsStagingFailure() {
        doThrow(new QueryTimeoutException("source timed out")).when(catalogSearch).searchAndStage(DUNE);

        assertThatCode(() -> stager.stage(DUNE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("swallows an upstream 5xx from a source so a degraded source never reaches the caller")
    void swallowsUpstreamServerError() {
        final WebClientResponseException badGateway = WebClientResponseException.create(
            HTTP_BAD_GATEWAY, "Bad Gateway", HttpHeaders.EMPTY, new byte[0], null);
        doThrow(badGateway).when(catalogSearch).searchAndStage(DUNE);

        assertThatCode(() -> stager.stage(DUNE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("retries a query whose first submission the executor rejected")
    void retriesAfterRejection() {
        final AtomicInteger submissions = new AtomicInteger();
        final boolean[] reject = {true};
        final Executor flaky = task -> {
            submissions.incrementAndGet();
            if (reject[0]) {
                throw new RejectedExecutionException("pool full");
            }
            task.run();
        };
        final SearchMissStager flakyStager = new SearchMissStager(catalogSearch, flaky, DEDUP_WINDOW);

        flakyStager.stage(DUNE);
        reject[0] = false;
        flakyStager.stage(DUNE);

        assertThat(submissions.get()).isEqualTo(2);
        verify(catalogSearch, times(1)).searchAndStage(DUNE);
    }

    @Test
    @DisplayName("stages distinct queries independently")
    void stagesDistinctQueries() {
        stager.stage(DUNE);
        stager.stage(FOUNDATION);

        verify(catalogSearch).searchAndStage(DUNE);
        verify(catalogSearch).searchAndStage(FOUNDATION);
        verify(catalogSearch, never()).searchAndStage("asimov");
    }
}
