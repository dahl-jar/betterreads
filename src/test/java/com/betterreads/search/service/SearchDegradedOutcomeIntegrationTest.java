package com.betterreads.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.search.dto.SearchOutcome;
import com.betterreads.support.ContainerizedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that when Meilisearch is unreachable, a search reports the empty result as degraded so
 * the caller can tell a backend outage from a real zero-hit answer.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "meilisearch.host=http://localhost:1",
    "meilisearch.master-key=unused",
    "meilisearch.index-name=books-test"
})
class SearchDegradedOutcomeIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    private BookSearchService searchService;

    @Test
    @DisplayName("flags the empty result as degraded when Meilisearch is unreachable")
    void flagsDegradedOnOutage() {
        final SearchOutcome outcome = searchService.searchOutcome("anything", 0, 20);

        assertThat(outcome.degraded()).isTrue();
        assertThat(outcome.result().totalHits()).isZero();
        assertThat(outcome.result().hits()).isEmpty();
    }
}
