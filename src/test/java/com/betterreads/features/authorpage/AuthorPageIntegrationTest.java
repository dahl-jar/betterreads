package com.betterreads.features.authorpage;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betterreads.testsupport.CatalogRows;
import com.betterreads.testsupport.ContainerizedTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "betterreads.catalog.staging.poll-enabled=false")
class AuthorPageIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String PATH = "/api/v1/authors/{authorId}";

    private static final String STORMLIGHT = "The Stormlight Archive";

    private static final int ELANTRIS_YEAR = 2005;

    private static final int KINGS_YEAR = 2010;

    private static final int RADIANCE_YEAR = 2014;

    private static final String SANDERSON = "Brandon Sanderson";

    private static final String ELANTRIS = "Elantris";

    private static final String WAY_OF_KINGS = "The Way of Kings";

    private static final String WORDS_OF_RADIANCE = "Words of Radiance";

    private static final long UNKNOWN_ID = 999_999L;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        jdbc.update("DELETE FROM book");
        jdbc.update("DELETE FROM author");
    }

    @Test
    void shouldListPromotedBooksInSeriesOrder() throws Exception {
        final long sanderson = author(SANDERSON);
        credit(book(ELANTRIS, null, null, ELANTRIS_YEAR), sanderson);
        credit(book(WORDS_OF_RADIANCE, STORMLIGHT, 2, RADIANCE_YEAR), sanderson);
        credit(book(WAY_OF_KINGS, STORMLIGHT, 1, KINGS_YEAR), sanderson);

        mockMvc.perform(get(PATH, sanderson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value(SANDERSON))
            .andExpect(jsonPath("$.data.books[0].title").value(WAY_OF_KINGS))
            .andExpect(jsonPath("$.data.books[1].title").value(WORDS_OF_RADIANCE))
            .andExpect(jsonPath("$.data.books[2].title").value(ELANTRIS))
            .andExpect(jsonPath("$.data.books[2].role").value("AUTHOR"))
            .andExpect(jsonPath("$.data.books[2].firstPublishYear").value(ELANTRIS_YEAR));
    }

    @Test
    void shouldRedirectMergedAuthor() throws Exception {
        final long survivor = author("Stephen King");
        jdbc.update("INSERT INTO author_merge (merged_author_id, author_id, merged_name) VALUES (?, ?, ?)",
            UNKNOWN_ID, survivor, "STEPHEN KING");

        mockMvc.perform(get(PATH, UNKNOWN_ID))
            .andExpect(status().is(HttpStatus.PERMANENT_REDIRECT.value()))
            .andExpect(header().string("Location", endsWith("/api/v1/authors/" + survivor)));
    }

    @Test
    void shouldReturnNotFoundForUnknownAuthor() throws Exception {
        mockMvc.perform(get(PATH, UNKNOWN_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    private long author(final String name) {
        return CatalogRows.author(jdbc, name);
    }

    private long book(
        final String title, final @Nullable String series, final @Nullable Integer position, final int year) {
        return jdbc.queryForObject("""
            INSERT INTO book (title, dedup_key, series_name, series_position, first_publish_year)
            VALUES (?, ?, ?, ?, ?)
            RETURNING book_id
            """, Long.class, title, title, series, position, year);
    }

    private void credit(final long bookId, final long authorId) {
        jdbc.update("INSERT INTO book_author (book_id, author_id) VALUES (?, ?)", bookId, authorId);
    }
}
