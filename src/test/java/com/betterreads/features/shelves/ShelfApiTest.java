package com.betterreads.features.shelves;

import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.RegisteredUserTest;

import java.math.BigDecimal;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.betterreads.testsupport.Accounts.AUTH_HEADER;
import static com.betterreads.testsupport.Accounts.BEARER_PREFIX;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

abstract class ShelfApiTest extends RegisteredUserTest {

    protected static final String SHELF_URL = "/api/v1/me/books";

    protected static final String HOBBIT_KEY = "OL262758W";

    protected static final String WANT_TO_READ = "WANT_TO_READ";

    protected static final String CURRENTLY_READING = "CURRENTLY_READING";

    protected static final String FINISHED = "FINISHED";

    protected static final String STATUS_FIELD = "status";

    protected static final String STATUS_SUFFIX = "/status";

    protected static final String RATED_KEY = "OL27448W";

    private static final BigDecimal SOURCE_AVERAGE = new BigDecimal("4.10");

    private static final String HOBBIT_TITLE = "The Hobbit";

    private static final String RATED_TITLE = "The Way of Kings";

    @Autowired
    protected BookRepository bookRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
        jdbcTemplate.update("DELETE FROM user_book_collection");
        resetToDune();
        Books.seedBook(bookRepository, HOBBIT_KEY, HOBBIT_TITLE);
        final Book rated = Books.book(RATED_KEY, RATED_TITLE);
        rated.setAverageRating(SOURCE_AVERAGE);
        bookRepository.save(rated);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected ResultActions putStatus(final String token, final String key, final String value)
        throws Exception {
        return mockMvc.perform(put(SHELF_URL + "/" + key + STATUS_SUFFIX)
            .header(AUTH_HEADER, BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody(STATUS_FIELD, value)));
    }

    protected String jsonBody(final String field, final Object value) {
        return objectMapper.writeValueAsString(Map.of(field, value));
    }
}
