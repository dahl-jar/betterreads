package com.betterreads.integration.image;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.port.FetchedImage;
import com.betterreads.integration.WireMockFixture;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class CoverFetchClientWireMockTest extends WireMockFixture {

    private static final int MAX_FETCH_BYTES = 8 * 1024 * 1024;

    private static final int PAST_DEFAULT_BUFFER_BYTES = 5 * 1024 * 1024;

    private static final int OVER_LIMIT_BYTES = MAX_FETCH_BYTES + 1;

    private static final int HTTP_OK = 200;

    private static final int HTTP_FOUND = 302;

    private static final String COVER_PATH = "/b/id/1-L.jpg";

    private static final String REDIRECT_PATH = "/cdn/1-L.jpg";

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String LOCATION_HEADER = "Location";

    private static final String JPEG_TYPE = "image/jpeg";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private final WebClient webClient = new CoverFetchWebClientConfig(
        new CoverFetchProperties(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, MAX_FETCH_BYTES)).coverFetchWebClient();

    private final CoverFetchClient client = new CoverFetchClient(webClient, allowAllGuard());

    private static CoverUrlGuard allowAllGuard() {
        return new CoverUrlGuard() {
            @Override
            public boolean isAllowed(final String url) {
                return true;
            }
        };
    }

    private static ResponseDefinitionBuilder jpegResponse(final byte[] body) {
        return aResponse().withStatus(HTTP_OK).withHeader(CONTENT_TYPE_HEADER, JPEG_TYPE).withBody(body);
    }

    private static ResponseDefinitionBuilder redirectTo(final String path) {
        return aResponse().withStatus(HTTP_FOUND).withHeader(LOCATION_HEADER, baseUrl() + path);
    }

    @Test
    @DisplayName("a 200 returns the body and content type")
    void returnsBodyAndContentType() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(jpegResponse(JPEG)));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

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

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("a 302 redirect is followed to the body, as OpenLibrary covers do")
    void followsRedirectToBody() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(redirectTo(REDIRECT_PATH)));
        WIREMOCK.stubFor(get(urlPathEqualTo(REDIRECT_PATH)).willReturn(jpegResponse(JPEG)));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).get().extracting(FetchedImage::bytes).isEqualTo(JPEG);
    }

    @Test
    @DisplayName("a cover larger than the default codec buffer downloads in full")
    void coverPastDefaultBufferDownloads() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(jpegResponse(new byte[PAST_DEFAULT_BUFFER_BYTES])));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched)
            .as("should size the buffer to the configured byte limit")
            .get()
            .satisfies(image -> assertThat(image.bytes()).hasSize(PAST_DEFAULT_BUFFER_BYTES));
    }

    @Test
    @DisplayName("a cover over the configured byte limit resolves to empty")
    void coverOverLimitIsEmpty() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(jpegResponse(new byte[OVER_LIMIT_BYTES])));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched)
            .as("should skip an oversized cover")
            .isEmpty();
    }

    @Test
    @DisplayName("a redirect to a target the guard refuses resolves to empty")
    void redirectToRefusedTargetIsEmpty() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(redirectTo(REDIRECT_PATH)));
        WIREMOCK.stubFor(get(urlPathEqualTo(REDIRECT_PATH)).willReturn(jpegResponse(JPEG)));
        final CoverFetchClient guarded = new CoverFetchClient(webClient, new CoverUrlGuard() {
            @Override
            public boolean isAllowed(final String url) {
                return !url.endsWith(REDIRECT_PATH);
            }
        });

        final Optional<FetchedImage> fetched = guarded.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched)
            .as("should check the redirect target with the guard before the hop")
            .isEmpty();
    }

    @Test
    @DisplayName("a redirect loop past the hop limit resolves to empty")
    void redirectLoopIsBounded() {
        WIREMOCK.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(redirectTo(COVER_PATH)));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).isEmpty();
    }
}
