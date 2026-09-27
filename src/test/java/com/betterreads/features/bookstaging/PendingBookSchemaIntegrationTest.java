package com.betterreads.features.bookstaging;

import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookRepository;
import com.betterreads.testsupport.ContainerizedTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false"
})
class PendingBookSchemaIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String ISBN = DuneBooks.ISBN;

    private static final String TITLE = DuneBooks.TITLE;

    @Autowired
    private PendingBookRepository pendingBooks;

    @BeforeEach
    void clearPending() {
        pendingBooks.deleteAll();
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
