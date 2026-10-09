package com.betterreads.clients.itunes;

import static com.betterreads.clients.itunes.ItunesSearchJson.ARTWORK;
import static com.betterreads.clients.itunes.ItunesSearchJson.STORE;
import static com.betterreads.clients.itunes.ItunesSearchJson.THUMBNAIL;
import static com.betterreads.clients.itunes.ItunesSearchJson.search;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.betterreads.clients.WireMockFixture;
import com.betterreads.ratelimit.RateLimiter;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = {ItunesApiWireMockTest.StubBeans.class, ItunesTestWebClientConfig.class, ItunesApi.class},
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(ItunesProperties.class)
class ItunesApiWireMockTest extends WireMockFixture {

    private static final String ISBN = "9780765326355";

    private static final String LOOKUP_PATH = "/lookup";

    private static final String EBOOK = "ebook";

    private static final AtomicBoolean PERMITS = new AtomicBoolean(true);

    @Autowired
    private ItunesApi api;

    @DynamicPropertySource
    static void wireProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "itunes", "");
        registry.add("itunes.rate-per-minute", () -> 1);
    }

    @TestConfiguration
    static class StubBeans {

        @Bean
        RateLimiter itunesRateLimiter() {
            return maxWait -> PERMITS.get();
        }
    }

    @BeforeEach
    void grantPermits() {
        PERMITS.set(true);
    }

    @Test
    void shouldThrowWhenNoRatePermitIsFree() {
        PERMITS.set(false);

        assertThatThrownBy(() -> api.lookupByIsbn(ISBN)).isInstanceOf(ItunesUnavailableException.class);
        WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo(LOOKUP_PATH)));
    }

    @Test
    void shouldAskForTheLargestArtwork() {
        stubLookup(okJson(search().withBook(EBOOK, THUMBNAIL, STORE).toString()));

        final Optional<ItunesBook> book = api.lookupByIsbn(ISBN);

        assertThat(book).contains(new ItunesBook(ARTWORK, STORE));
    }

    @Test
    void shouldSkipAnAudiobookListedBeforeTheEbook() {
        stubLookup(okJson(search()
            .withBook("audiobook", ItunesSearchJson.AUDIOBOOK_THUMBNAIL, STORE)
            .andBook(EBOOK, THUMBNAIL, STORE)
            .toString()));

        final Optional<ItunesBook> book = api.lookupByIsbn(ISBN);

        assertThat(book).contains(new ItunesBook(ARTWORK, STORE));
    }

    @Test
    void shouldThrowWhenApplesServerFails() {
        stubLookup(aResponse().withStatus(HTTP_SERVER_ERROR));

        assertThatThrownBy(() -> api.lookupByIsbn(ISBN)).isInstanceOf(ItunesUnavailableException.class);
    }

    @Test
    void shouldReturnNothingWhenAppleRejectsTheLookup() {
        stubLookup(aResponse().withStatus(HTTP_NOT_FOUND));

        final Optional<ItunesBook> book = api.lookupByIsbn(ISBN);

        assertThat(book).isEmpty();
    }

    @Test
    void shouldThrowWhenAppleSendsAnUnreadableReply() {
        stubLookup(okJson("<html>maintenance</html>"));

        assertThatThrownBy(() -> api.lookupByIsbn(ISBN)).isInstanceOf(ItunesUnavailableException.class);
    }

    private static void stubLookup(final ResponseDefinitionBuilder response) {
        WIREMOCK.stubFor(get(urlPathEqualTo(LOOKUP_PATH)).withQueryParam("isbn", equalTo(ISBN)).willReturn(response));
    }
}
