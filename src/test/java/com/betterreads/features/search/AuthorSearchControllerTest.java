package com.betterreads.features.search;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "betterreads.catalog.staging.poll-enabled=false")
class AuthorSearchControllerTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String PATH = "/api/v1/search/authors";

    private static final int PAGE = 20;

    private static final String QUERY = "king";

    private static final String KING = "Stephen King";

    private static final long KING_ID = 2L;

    private static final int KING_BOOKS = 94;

    private static final double KING_POPULARITY = 61.0;

    @MockitoBean
    private AuthorSearchService searchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
    }

    @Test
    void shouldRejectBlankQuery() throws Exception {
        mockMvc.perform(get(PATH).param("q", " "))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnHitsInEnvelope() throws Exception {
        final AuthorSearchDocument king = new AuthorSearchDocument(
            KING_ID, KING, "King", List.of(), null, KING_BOOKS, KING_POPULARITY, List.of("Carrie"));
        when(searchService.search(QUERY, 0, PAGE)).thenReturn(new AuthorSearchResult(List.of(king), 1, 0, PAGE));

        mockMvc.perform(get(PATH).param("q", QUERY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value(KING))
            .andExpect(jsonPath("$.data[0].authorId").value(KING_ID))
            .andExpect(jsonPath("$.meta.total").value(1));
    }
}
