package com.betterreads.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.repository.PendingBookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the V25 {@code pending_book} schema against a real Postgres: a candidate round-trips
 * through the repository, and the identity columns are unique so the same book cannot be staged
 * twice under different rows.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-256-bits-long-padding-padding",
    "jwt.issuer=betterreads-it",
    "betterreads.catalog.staging.poll-enabled=false"
})
class PendingBookSchemaIntegrationTest {

    private static final String ISBN = "9780441013593";

    private static final String TITLE = "Dune";

    private static final String STATUS_PENDING = "PENDING";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @Autowired
    private PendingBookRepository pendingBooks;

    @BeforeEach
    void clearPending() {
        pendingBooks.deleteAll();
    }

    @Test
    @DisplayName("a staged candidate round-trips through the repository")
    void candidateRoundTrips() {
        final PendingBook candidate = new PendingBook();
        candidate.setDedupKey(ISBN);
        candidate.setIsbn13(ISBN);
        candidate.setTitle(TITLE);

        pendingBooks.save(candidate);

        assertThat(pendingBooks.findByIsbn13(ISBN))
            .isPresent()
            .get()
            .satisfies(found -> {
                assertThat(found.getTitle()).isEqualTo(TITLE);
                assertThat(found.getStatus()).isEqualTo(STATUS_PENDING);
            });
    }

    @Test
    @DisplayName("two candidates with the same ISBN under different keys cannot both be stored")
    void duplicateIsbnIsRejected() {
        final PendingBook first = new PendingBook();
        first.setDedupKey("key-one");
        first.setIsbn13(ISBN);
        first.setTitle(TITLE);
        pendingBooks.saveAndFlush(first);

        final PendingBook second = new PendingBook();
        second.setDedupKey("key-two");
        second.setIsbn13(ISBN);
        second.setTitle("Dune reprint");

        assertThatThrownBy(() -> pendingBooks.saveAndFlush(second))
            .as("the unique constraint on isbn13 stops the same ISBN landing under two rows")
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
