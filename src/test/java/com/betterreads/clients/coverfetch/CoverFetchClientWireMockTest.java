package com.betterreads.clients.coverfetch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.clients.WireMockFixture;
import com.betterreads.clients.http.PublicUrlGuard;
import com.betterreads.clients.http.UrlGuards;
import com.betterreads.images.Image;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class CoverFetchClientWireMockTest extends WireMockFixture {

    private static final int MAX_FETCH_BYTES = 8 * 1024 * 1024;

    private static final int PAST_DEFAULT_BUFFER_BYTES = 5 * 1024 * 1024;

    private static final int OVER_LIMIT_BYTES = MAX_FETCH_BYTES + 1;

    private static final int HTTP_OK = 200;

    private static final int SHORT_READ_MS = 1000;

    private static final int TRICKLE_MS = 3000;

    private static final int HTTP_FOUND = 302;

    private static final String COVER_PATH = "/b/id/1-L.jpg";

    private static final String REDIRECT_PATH = "/cdn/1-L.jpg";

    private static final String SECOND_HOP_PATH = "/cdn/hop-2.jpg";

    private static final String THIRD_HOP_PATH = "/cdn/hop-3.jpg";

    private static final int REQUESTS_UNTIL_HOP_LIMIT = 4;

    private static final String OCTET_STREAM_TYPE = "application/octet-stream";

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String LOCATION_HEADER = "Location";

    private static final String JPEG_TYPE = "image/jpeg";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private final CoverFetchProperties properties =
        new CoverFetchProperties(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, MAX_FETCH_BYTES);

    private final WebClient webClient =
        new CoverFetchWebClientConfig(properties, UrlGuards.allowing(true)).coverFetchWebClient();

    private final CoverFetchClient client = new CoverFetchClient(webClient, UrlGuards.allowing(true), properties);

    private static ResponseDefinitionBuilder jpegResponse(final byte[] body) {
        return aResponse().withStatus(HTTP_OK).withHeader(CONTENT_TYPE_HEADER, JPEG_TYPE).withBody(body);
    }

    private static ResponseDefinitionBuilder redirectTo(final String path) {
        return aResponse().withStatus(HTTP_FOUND).withHeader(LOCATION_HEADER, baseUrl() + path);
    }

    private static void stubRedirectToCover() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(redirectTo(REDIRECT_PATH)));
        WIREMOCK.stubFor(get(urlPathEqualTo(REDIRECT_PATH)).willReturn(jpegResponse(JPEG)));
    }

    @Test
    @DisplayName("a 200 returns the body and content type")
    void returnsBodyAndContentType() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(jpegResponse(JPEG)));

        final Optional<Image> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).get()
            .satisfies(image -> {
                assertThat(image.bytes()).isEqualTo(JPEG);
                assertThat(image.contentType()).isEqualTo(JPEG_TYPE);
            });
    }

    @Test
    @DisplayName("a 404 resolves to empty")
    void notFoundIsEmpty() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

        final Optional<Image> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("should fall back to octet-stream when the response has no content type")
    void shouldFallBackToOctetStreamWithoutContentType() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(aResponse().withStatus(HTTP_OK).withBody(JPEG)));

        final Optional<Image> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).get().extracting(Image::contentType).isEqualTo(OCTET_STREAM_TYPE);
    }

    @Test
    @DisplayName("a cover larger than the default codec buffer downloads in full")
    void coverPastDefaultBufferDownloads() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(jpegResponse(new byte[PAST_DEFAULT_BUFFER_BYTES])));

        final Optional<Image> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched)
            .as("should size the buffer to the configured byte limit")
            .get()
            .satisfies(image -> assertThat(image.bytes()).hasSize(PAST_DEFAULT_BUFFER_BYTES));
    }

    @Test
    @DisplayName("a cover over the configured byte limit resolves to empty")
    void coverOverLimitIsEmpty() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(jpegResponse(new byte[OVER_LIMIT_BYTES])));

        final Optional<Image> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched)
            .as("should skip an oversized cover")
            .isEmpty();
    }

    @Test
    void shouldGiveUpOnACoverThatKeepsTrickling() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH))
            .willReturn(jpegResponse(JPEG).withChunkedDribbleDelay(JPEG.length, TRICKLE_MS)));
        final CoverFetchProperties impatient =
            new CoverFetchProperties(CONNECT_TIMEOUT_MS, SHORT_READ_MS, MAX_FETCH_BYTES);
        final CoverFetchClient slow = new CoverFetchClient(
            new CoverFetchWebClientConfig(impatient, UrlGuards.allowing(true)).coverFetchWebClient(),
            UrlGuards.allowing(true), impatient);

        final Optional<Image> fetched = slow.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).isEmpty();
    }

    @Nested
    @DisplayName("redirects")
    class Redirects {

        @Test
        @DisplayName("a 302 redirect is followed to the body, as OpenLibrary covers do")
        void followsRedirectToBody() {
            stubRedirectToCover();

            final Optional<Image> fetched = client.fetch(baseUrl() + COVER_PATH);

            assertThat(fetched).get().extracting(Image::bytes).isEqualTo(JPEG);
        }

        @Test
        @DisplayName("a redirect to a target the guard refuses resolves to empty")
        void redirectToRefusedTargetIsEmpty() {
            stubRedirectToCover();
            final CoverFetchClient guarded = new CoverFetchClient(webClient, new PublicUrlGuard() {
                @Override
                public boolean isAllowed(final String url) {
                    return !url.endsWith(REDIRECT_PATH);
                }
            }, properties);

            final Optional<Image> fetched = guarded.fetch(baseUrl() + COVER_PATH);

            assertThat(fetched)
                .as("should check the redirect target with the guard before the hop")
                .isEmpty();
        }

        @Test
        @DisplayName("a redirect loop past the hop limit resolves to empty")
        void redirectLoopIsBounded() {
            WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(redirectTo(COVER_PATH)));

            final Optional<Image> fetched = client.fetch(baseUrl() + COVER_PATH);

            assertThat(fetched).isEmpty();
            WIREMOCK.verify(REQUESTS_UNTIL_HOP_LIMIT, getRequestedFor(urlPathEqualTo(COVER_PATH)));
        }

        @Test
        @DisplayName("should return the body after the last allowed redirect")
        void shouldReturnBodyAfterLastAllowedRedirect() {
            WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(redirectTo(REDIRECT_PATH)));
            WIREMOCK.stubFor(get(urlPathEqualTo(REDIRECT_PATH)).willReturn(redirectTo(SECOND_HOP_PATH)));
            WIREMOCK.stubFor(get(urlPathEqualTo(SECOND_HOP_PATH)).willReturn(redirectTo(THIRD_HOP_PATH)));
            WIREMOCK.stubFor(get(urlPathEqualTo(THIRD_HOP_PATH)).willReturn(jpegResponse(JPEG)));

            final Optional<Image> fetched = client.fetch(baseUrl() + COVER_PATH);

            assertThat(fetched).get().extracting(Image::bytes).isEqualTo(JPEG);
        }
    }
}
