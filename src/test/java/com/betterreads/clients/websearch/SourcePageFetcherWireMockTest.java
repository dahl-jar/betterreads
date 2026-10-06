package com.betterreads.clients.websearch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.betterreads.clients.WireMockFixture;
import com.betterreads.clients.http.PublicUrlGuard;
import com.betterreads.clients.http.Redirects;
import com.betterreads.clients.http.UrlGuards;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SourcePageFetcherWireMockTest extends WireMockFixture {

    private static final String HOST = "localhost";

    private static final String PAGE_PATH = "/book/red-rising";

    private static final String OTHER_PATH = "/book/elsewhere";

    private static final String LOCATION = "Location";

    private static final String HTTPS = "https";

    private static final int MAX_BYTES = 4096;

    private static final int HTTP_OK = 200;

    private static final int HTTP_FOUND = 302;

    private static final int HTTP_FORBIDDEN = 403;

    private static final int SHORT_TIMEOUT_MS = 1000;

    private static final int DRIBBLE_CHUNKS = 5;

    private static final int DRIBBLE_MS = 3000;

    private static final String BLURB = "Darrow is a Red.";

    private static final String PARAGRAPH = "<p>" + BLURB + "</p>";

    private static final String OTHER_HOST = "http://127.0.0.1:";

    private static final List<String> ANY_SCHEME = List.of("http", HTTPS);

    private final SourcePageFetcher fetcher = build(true, ANY_SCHEME, WebSearchSamples.PAGE_TIMEOUT_MS);

    private static SourcePageFetcher build(final boolean guardAllows, final List<String> schemes, final int timeout) {
        return build(UrlGuards.allowing(guardAllows), schemes, timeout);
    }

    private static SourcePageFetcher build(final PublicUrlGuard guard, final List<String> schemes, final int timeout) {
        final WebSearchProperties properties = WebSearchSamples.pages(HOST, MAX_BYTES, schemes, timeout);
        return new SourcePageFetcher(new SourcePageWebClientConfig(properties, guard).sourcePageWebClient(),
            guard, properties);
    }

    private static ResponseDefinitionBuilder html(final String body) {
        return aResponse().withStatus(HTTP_OK).withHeader("Content-Type", "text/html; charset=utf-8").withBody(body);
    }

    private static void stubPage(final String body) {
        WIREMOCK.stubFor(get(urlPathEqualTo(PAGE_PATH)).willReturn(html(body)));
    }

    private static void stubRedirect(final String from, final String location) {
        WIREMOCK.stubFor(get(urlPathEqualTo(from)).willReturn(aResponse().withStatus(HTTP_FOUND)
            .withHeader(LOCATION, location)));
    }

    @Nested
    class Refusals {

        @Test
        void shouldRefuseAHostOffTheAllowlist() {
            stubPage(PARAGRAPH);

            final Optional<String> text = fetcher.text(OTHER_HOST + WIREMOCK.port() + PAGE_PATH);

            assertThat(text).isEmpty();
        }

        @Test
        void shouldRefuseAUrlTheGuardRejects() {
            stubPage(PARAGRAPH);
            final SourcePageFetcher guarded = build(false, ANY_SCHEME, WebSearchSamples.PAGE_TIMEOUT_MS);

            final Optional<String> text = guarded.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
            WIREMOCK.verify(0, getRequestedFor(anyUrl()));
        }

        @Test
        void shouldRefuseAHostThatResolvesPrivateAtConnect() {
            stubPage(PARAGRAPH);
            final SourcePageFetcher rebound =
                build(UrlGuards.checkingOnlyAtConnect(), ANY_SCHEME, WebSearchSamples.PAGE_TIMEOUT_MS);

            final Optional<String> text = rebound.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
            WIREMOCK.verify(0, getRequestedFor(anyUrl()));
        }

        @Test
        void shouldRefusePlainHttpWhenOnlyHttpsIsAllowed() {
            stubPage(PARAGRAPH);
            final SourcePageFetcher httpsOnly = build(true, List.of(HTTPS), WebSearchSamples.PAGE_TIMEOUT_MS);

            final Optional<String> text = httpsOnly.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
            WIREMOCK.verify(0, getRequestedFor(anyUrl()));
        }
    }

    @Nested
    class Hops {

        @Test
        void shouldFollowARelativeRedirectOnTheSameHost() {
            stubRedirect(PAGE_PATH, OTHER_PATH);
            WIREMOCK.stubFor(get(urlPathEqualTo(OTHER_PATH)).willReturn(html(PARAGRAPH)));

            final Optional<String> text = fetcher.text(baseUrl() + PAGE_PATH);

            assertThat(text).contains(BLURB);
        }

        @Test
        void shouldRefuseARedirectToAnotherHost() {
            stubRedirect(PAGE_PATH, OTHER_HOST + WIREMOCK.port() + OTHER_PATH);
            WIREMOCK.stubFor(get(urlPathEqualTo(OTHER_PATH)).willReturn(html(PARAGRAPH)));

            final Optional<String> text = fetcher.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
        }

        @Test
        void shouldStopAfterThreeRedirects() {
            stubRedirect(PAGE_PATH, PAGE_PATH);

            final Optional<String> text = fetcher.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
            WIREMOCK.verify(Redirects.MAX_HOPS + 1, getRequestedFor(urlPathEqualTo(PAGE_PATH)));
        }

        @Test
        void shouldReturnEmptyForAMalformedRedirect() {
            stubRedirect(PAGE_PATH, "/a b");

            final Optional<String> text = fetcher.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
        }
    }

    @Nested
    class Responses {

        @Test
        void shouldReturnTheVisibleTextOfAPage() {
            stubPage("<html><head><title>Tab title</title></head><body><p>Darrow is a <b>Red</b>.</p></body></html>");

            final Optional<String> text = fetcher.text(baseUrl() + PAGE_PATH);

            assertThat(text).contains(BLURB);
        }

        @Test
        void shouldReturnTheHeadingAndTheFinalUrl() {
            stubRedirect(PAGE_PATH, OTHER_PATH);
            WIREMOCK.stubFor(get(urlPathEqualTo(OTHER_PATH)).willReturn(html(
                "<html><head><title>Red Rising | Publisher</title></head><body><h1>Red Rising</h1>"
                    + PARAGRAPH + "</body></html>")));

            final Optional<SourcePage> page = fetcher.page(baseUrl() + PAGE_PATH);

            assertThat(page).get().extracting(SourcePage::url).isEqualTo(baseUrl() + OTHER_PATH);
            assertThat(page).get().extracting(SourcePage::heading).isEqualTo("Red Rising | Publisher Red Rising");
        }

        @Test
        void shouldReturnEmptyForAPageWithNoVisibleText() {
            stubPage("<html><body><script>var tracker = 1;</script></body></html>");

            final Optional<String> text = fetcher.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
        }

        @Test
        void shouldReturnEmptyOn403() {
            WIREMOCK.stubFor(get(urlPathEqualTo(PAGE_PATH)).willReturn(aResponse().withStatus(HTTP_FORBIDDEN)
                .withBody(PARAGRAPH)));

            final Optional<String> text = fetcher.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
        }

        @Test
        void shouldReturnEmptyForAnOversizedBody() {
            stubPage("x".repeat(2 * MAX_BYTES));

            final Optional<String> text = fetcher.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
        }

        @Test
        void shouldGiveUpOnAPageThatKeepsTrickling() {
            WIREMOCK.stubFor(get(urlPathEqualTo(PAGE_PATH)).willReturn(
                html(PARAGRAPH.repeat(DRIBBLE_CHUNKS)).withChunkedDribbleDelay(DRIBBLE_CHUNKS, DRIBBLE_MS)));
            final SourcePageFetcher impatient = build(true, ANY_SCHEME, SHORT_TIMEOUT_MS);

            final Optional<String> text = impatient.text(baseUrl() + PAGE_PATH);

            assertThat(text).isEmpty();
        }
    }
}
