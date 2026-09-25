package com.betterreads.integration.googlebooks.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.Fixtures;
import com.betterreads.integration.WireMockFixture;
import com.betterreads.integration.googlebooks.GoogleBooksProperties;
import com.betterreads.integration.googlebooks.GoogleBooksWebClientConfig;
import com.betterreads.integration.googlebooks.mapper.GoogleBooksMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = {
        GoogleBooksWebClientConfig.class,
        GoogleBooksClientImpl.class,
        GoogleBooksMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(GoogleBooksProperties.class)
class GoogleBooksClientWireMockTest extends WireMockFixture {

    private static final int DUNE_REISSUE_YEAR = 2016;

    private static final int DUNE_PAGE_COUNT = 412;

    private static final String SEARCH_PATH = "/volumes";

    private static final String VOLUME_ID = "TtkxEAAAQBAJ";

    private static final String VOLUME_PATH = SEARCH_PATH + "/" + VOLUME_ID;

    private static final String DUNE_TITLE = "Dune";

    @Autowired
    private GoogleBooksClientImpl client;

    @DynamicPropertySource
    static void googleBooksProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "googlebooks", "");
        registry.add("googlebooks.api-key", () -> "test-key");
    }

    @Nested
    @DisplayName("fetchByTitleAuthor")
    class FetchByTitleAuthor {

        @Test
        @DisplayName("parses the volume, strips HTML, picks ISBN-13, and keeps the page count")
        void parsesVolumeCleanly() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(okJson(Fixtures.read("googlebooks/search.json"))));

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, "Frank Herbert");

            assertThat(result)
                .isPresent()
                .get()
                .satisfies(book -> {
                    assertThat(book.source()).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
                    assertThat(book.googleBooksVolumeId()).isEqualTo(VOLUME_ID);
                    assertThat(book.isbn13()).isEqualTo("9780143111580");
                    assertThat(book.pageCount()).isEqualTo(DUNE_PAGE_COUNT);
                    assertThat(book.publicationYear()).isEqualTo(DUNE_REISSUE_YEAR);
                    assertThat(book.description())
                        .as("should strip the <p> and <b> tags from Google's description")
                        .isEqualTo("Set on the desert planet Arrakis.");
                });
        }
    }

    @Nested
    @DisplayName("fetchByVolumeId")
    class FetchByVolumeId {

        @Test
        @DisplayName("maps the single-volume response")
        void mapsSingleVolumeResponse() {
            WIREMOCK.stubFor(get(urlPathEqualTo(VOLUME_PATH))
                .willReturn(okJson(Fixtures.read("googlebooks/volume.json"))));

            final Optional<SourceBook> result = client.fetchByVolumeId(VOLUME_ID);

            assertThat(result)
                .isPresent()
                .get()
                .extracting(SourceBook::title)
                .isEqualTo(DUNE_TITLE);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("a 404 on the search resolves to empty")
        void notFoundIsEmpty() {
            WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

            final Optional<SourceBook> result = client.fetchByTitleAuthor("Nope", "Nobody");

            assertThat(result).isEmpty();
        }
    }
}
