package com.betterreads.clients.itunes;

import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.clients.WireMockFixture;
import com.betterreads.ratelimit.RateLimiter;
import static com.betterreads.clients.itunes.ItunesSearchJson.search;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = {
        ItunesDescriptionSourceWireMockTest.StubBeans.class,
        ItunesTestWebClientConfig.class,
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

    private static final String MEDIA = "media";

    private static final String EBOOK = "ebook";

    private static final AtomicBoolean RATE_BUDGET_LEFT = new AtomicBoolean(true);

    @Autowired
    private ItunesDescriptionSource source;

    @DynamicPropertySource
    static void wireProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "itunes", "");
        registry.add("itunes.rate-per-minute", () -> 1);
    }

    @BeforeEach
    void refillRateBudget() {
        RATE_BUDGET_LEFT.set(true);
    }

    @TestConfiguration
    static class StubBeans {

        @Bean
        RateLimiter itunesRateLimiter() {
            return maxWait -> RATE_BUDGET_LEFT.get();
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
        void shouldFindNothingWhenNoResultHasAUsableDescription() {
            stubSearch(search().withDescription(""));

            final Optional<String> description = source.fetch(byIsbn());

            assertThat(description).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({
            ",, ",
            "' ',, ",
            ", Howling Dark, ",
            ", Howling Dark, ' '",
            ", ' ', Christopher Ruocchio"
        })
        void shouldSkipTheSearchWithoutAnIsbnOrATitleAndAuthor(
            final String isbn, final String title, final String author) {
            final Optional<String> description = source.fetch(
                new DescriptionLookup(null, isbn, title, author, null, null));

            assertThat(description).isEmpty();
            WIREMOCK.verify(0, getRequestedFor(anyUrl()));
        }

        @Test
        void shouldSkipTheSearchWhenTheRateBudgetIsExhausted() {
            stubSearch(search());
            RATE_BUDGET_LEFT.set(false);

            final Optional<String> description = source.fetch(byIsbn());

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
        void shouldPreferTheFullerOfTwoUsableBlurbs() {
            final String shortBlurb = "Hadrian Marlowe sails into the dark of the war to come.";
            final String blurb = BLURB_START + " as Hadrian Marlowe walks a path that can only end in fire.";
            stubSearch(search().withDescription(shortBlurb).withResult(TITLE, blurb));

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
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).withQueryParam(MEDIA, equalTo(EBOOK))
            .willReturn(okJson(search.toString())));
    }

    private static void stubSearch(final String term, final ItunesSearchJson search) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).withQueryParam(MEDIA, equalTo(EBOOK))
            .withQueryParam("term", equalTo(term))
            .willReturn(okJson(search.toString())));
    }
}
