package com.betterreads.integration.itunes;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.DescriptionLookup;
import com.betterreads.common.ratelimit.RateLimiter;
import com.betterreads.common.web.WebClients;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Exercises the Apple Books description source against a stubbed HTTP boundary: the search returns a
 * marketing blurb with HTML, an empty result set resolves to empty, and the lookup falls back from
 * ISBN to title-and-author.
 */
@SpringBootTest(
    classes = {
        ItunesDescriptionSourceWireMockTest.StubBeans.class,
        ItunesApi.class,
        ItunesDescriptionSource.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(ItunesProperties.class)
class ItunesDescriptionSourceWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    private static final int READ_TIMEOUT_MS = 5000;

    private static final int HTTP_OK = 200;

    private static final String SEARCH_PATH = "/search";

    private static final String ISBN = "9780756413019";

    private static final String TITLE = "Howling Dark";

    private static final String AUTHOR = "Christopher Ruocchio";

    private static final String BLURB =
        "The second novel of the Sun Eater series merges space opera and epic fantasy as Hadrian "
        + "Marlowe continues down a path that can only end in fire.";

    private static final WireMockServer WIREMOCK = startServer();

    @Autowired
    private ItunesDescriptionSource source;

    @DynamicPropertySource
    static void wireProperties(final DynamicPropertyRegistry registry) {
        registry.add("itunes.base-url", () -> "http://localhost:" + WIREMOCK.port());
        registry.add("itunes.connect-timeout", () -> CONNECT_TIMEOUT_MS);
        registry.add("itunes.read-timeout", () -> READ_TIMEOUT_MS);
        registry.add("itunes.rate-per-minute", () -> 1);
    }

    @TestConfiguration
    static class StubBeans {

        @Bean
        WebClient itunesWebClient(final ItunesProperties properties) {
            return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout()).build();
        }

        @Bean
        RateLimiter itunesRateLimiter() {
            return maxWait -> true;
        }
    }

    @BeforeEach
    void resetStubs() {
        WIREMOCK.resetAll();
    }

    @AfterAll
    static void stopServer() {
        WIREMOCK.stop();
    }

    @Nested
    @DisplayName("fetch")
    class Fetch {

        @Test
        @DisplayName("source identity is iTunes")
        void sourceIdentity() {
            assertThat(source.source()).isEqualTo(BookFieldSource.ITUNES);
        }

        @Test
        @DisplayName("returns the search blurb for the ISBN")
        void returnsBlurbByIsbn() {
            stubSearch(resultJson(BLURB));

            final Optional<String> description = source.fetch(lookup());

            assertThat(description).contains(BLURB);
        }

        @Test
        @DisplayName("an empty result set resolves to empty")
        void emptyResultsAreEmpty() {
            stubSearch("{ \"resultCount\": 0, \"results\": [] }");

            final Optional<String> description = source.fetch(lookup());

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("a result with a blank description resolves to empty")
        void blankDescriptionIsEmpty() {
            stubSearch(resultJson(""));

            final Optional<String> description = source.fetch(lookup());

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("a lookup with no ISBN, title, or author resolves to empty without a call")
        void noKeysIsEmpty() {
            final Optional<String> description = source.fetch(
                new DescriptionLookup(null, null, null, null));

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("the title-author fallback accepts a result whose title matches")
        void titleAuthorFallbackAcceptsMatchingTitle() {
            stubSearch(resultJson(TITLE, BLURB));

            final Optional<String> description = source.fetch(
                new DescriptionLookup(null, null, TITLE, AUTHOR));

            assertThat(description).contains(BLURB);
        }

        @Test
        @DisplayName("the title-author fallback rejects a result for a different book")
        void titleAuthorFallbackRejectsWrongBook() {
            stubSearch(resultJson("A Completely Different Book", BLURB));

            final Optional<String> description = source.fetch(
                new DescriptionLookup(null, null, TITLE, AUTHOR));

            assertThat(description).isEmpty();
        }

        @Test
        @DisplayName("the title-author fallback rejects a result whose title is only a leading word")
        void titleAuthorFallbackRejectsLeadingWordMatch() {
            stubSearch(resultJson("Howling", BLURB));

            final Optional<String> description = source.fetch(
                new DescriptionLookup(null, null, TITLE, AUTHOR));

            assertThat(description).isEmpty();
        }
    }

    private static DescriptionLookup lookup() {
        return new DescriptionLookup("Q1", ISBN, TITLE, AUTHOR);
    }

    private static void stubSearch(final String body) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH))
            .willReturn(aResponse()
                .withStatus(HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private static String resultJson(final String description) {
        return resultJson(TITLE, description);
    }

    private static String resultJson(final String trackName, final String description) {
        return "{ \"resultCount\": 1, \"results\": [ { \"trackName\": \"" + trackName + "\", "
            + "\"description\": \"" + description + "\" } ] }";
    }

    private static WireMockServer startServer() {
        final WireMockServer server = new WireMockServer(0);
        server.start();
        return server;
    }
}
