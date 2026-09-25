package com.betterreads.integration.itunes;

import static com.betterreads.integration.itunes.ItunesSearchJson.search;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.port.DescriptionLookup;
import com.betterreads.common.ratelimit.RateLimiter;
import com.betterreads.common.web.WebClients;
import com.betterreads.integration.WireMockFixture;
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

@SpringBootTest(
    classes = {
        ItunesDescriptionSourceWireMockTest.StubBeans.class,
        ItunesApi.class,
        ItunesDescriptionSource.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(ItunesProperties.class)
class ItunesDescriptionSourceWireMockTest extends WireMockFixture {

    private static final String SEARCH_PATH = "/search";

    private static final String ISBN = "9780756413019";

    private static final String TITLE = "Howling Dark";

    private static final String AUTHOR = "Christopher Ruocchio";

    private static final String BLURB_START = "The second novel of the Sun Eater series";

    @Autowired
    private ItunesDescriptionSource source;

    @DynamicPropertySource
    static void wireProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "itunes", "");
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

    @Nested
    @DisplayName("fetch")
    class Fetch {

        @Test
        void shouldReturnTheBlurbFoundByIsbn() {
            stubSearch(search());

            final Optional<String> description = source.fetch(byIsbn());

            assertThat(description).hasValueSatisfying(blurb -> assertThat(blurb).startsWith(BLURB_START));
        }

        @Test
        void shouldReturnEmptyWhenTheOnlyResultHasABlankDescription() {
            stubSearch(search().withDescription(""));

            final Optional<String> description = source.fetch(byIsbn());

            assertThat(description).isEmpty();
        }

        @Test
        void shouldSkipTheSearchWhenTheLookupHasNoIsbnTitleOrAuthor() {
            final Optional<String> description = source.fetch(
                new DescriptionLookup(null, null, null, null, null, null));

            assertThat(description).isEmpty();
            WIREMOCK.verify(0, getRequestedFor(anyUrl()));
        }

        @Test
        void shouldSearchByTitleAndAuthorWhenTheIsbnFindsNothing() {
            stubSearch(ISBN, search().withoutResults());
            stubSearch(TITLE + " " + AUTHOR, search());

            final Optional<String> description = source.fetch(
                new DescriptionLookup(null, ISBN, TITLE, AUTHOR, null, null));

            assertThat(description).hasValueSatisfying(blurb -> assertThat(blurb).startsWith(BLURB_START));
        }

        @Test
        void shouldRejectATitleSearchResultForADifferentBook() {
            stubSearch(search().withTrackName("A Completely Different Book"));

            final Optional<String> description = source.fetch(byTitleAndAuthor());

            assertThat(description).isEmpty();
        }

        @Test
        void shouldPreferThePublisherBlurbOverAStoreMarketingFirstResult() {
            final String marketing = "Available only on Apple Books, this enhanced edition is an "
                + "amazing way to explore the rich world of the series. Stay on top of the story "
                + "lines with annotations, glossaries, and family trees.";
            final String blurb = BLURB_START + " as Hadrian Marlowe walks a path that can only end in fire.";
            stubSearch(search().withDescription(marketing).withResult(TITLE, blurb));

            final Optional<String> description = source.fetch(byTitleAndAuthor());

            assertThat(description).contains(blurb);
        }
    }

    private static DescriptionLookup byIsbn() {
        return new DescriptionLookup(null, ISBN, null, null, null, null);
    }

    private static DescriptionLookup byTitleAndAuthor() {
        return new DescriptionLookup(null, null, TITLE, AUTHOR, null, null);
    }

    private static void stubSearch(final ItunesSearchJson search) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson(search.toString())));
    }

    private static void stubSearch(final String term, final ItunesSearchJson search) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).withQueryParam("term", equalTo(term))
            .willReturn(okJson(search.toString())));
    }
}
