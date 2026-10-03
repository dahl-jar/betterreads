package com.betterreads.features.shelves;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.betterreads.testsupport.Accounts.AUTH_HEADER;
import static com.betterreads.testsupport.Accounts.BEARER_PREFIX;
import static com.betterreads.testsupport.Accounts.OTHER_USER;
import static com.betterreads.testsupport.Accounts.OTHER_USER_EMAIL;
import static com.betterreads.testsupport.Accounts.USER;
import static com.betterreads.testsupport.Accounts.USER_EMAIL;
import static com.betterreads.testsupport.Books.DUNE_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class MyShelfCountsIntegrationTest extends ShelfApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String MY_COUNTS_URL = SHELF_URL + "/counts";

    private static final String JSON_TOTAL_COUNT = "$.data.total";

    private static final int THREE_BOOKS = 3;

    @Nested
    @DisplayName("GET /me/books/counts")
    class MyCounts {

        @Test
        void shouldCountEveryBookOnTheCallersShelf() throws Exception {
            final String token = registerAndLogin(USER, USER_EMAIL);
            putStatus(token, DUNE_KEY, WANT_TO_READ);
            putStatus(token, HOBBIT_KEY, CURRENTLY_READING);
            putStatus(token, RATED_KEY, FINISHED);

            final ResultActions response = getMyCounts(token);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(THREE_BOOKS));
        }

        @Test
        void shouldNotCountAnotherReadersBooks() throws Exception {
            final String userToken = registerAndLogin(USER, USER_EMAIL);
            final String otherUserToken = registerAndLogin(OTHER_USER, OTHER_USER_EMAIL);
            putStatus(otherUserToken, DUNE_KEY, WANT_TO_READ);
            putStatus(userToken, HOBBIT_KEY, WANT_TO_READ);

            final ResultActions response = getMyCounts(userToken);

            response
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(1));
        }

        @Test
        void shouldRequireSignInForShelfCounts() throws Exception {
            final ResultActions response = mockMvc.perform(get(MY_COUNTS_URL));

            response.andExpect(status().isUnauthorized());
        }
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private ResultActions getMyCounts(final String token) throws Exception {
        return mockMvc.perform(get(MY_COUNTS_URL).header(AUTH_HEADER, BEARER_PREFIX + token));
    }
}
