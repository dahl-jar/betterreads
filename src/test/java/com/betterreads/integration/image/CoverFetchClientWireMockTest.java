package com.betterreads.integration.image;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.FetchedImage;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Fetches cover bytes from a stubbed origin: a 200 returns the body and content type, a 404 resolves
 * to empty so the mirror leaves the book on its external URL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoverFetchClientWireMockTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    private static final int READ_TIMEOUT_MS = 5000;

    private static final int MAX_FETCH_BYTES = 8 * 1024 * 1024;

    private static final int PAST_DEFAULT_BUFFER_BYTES = 5 * 1024 * 1024;

    private static final int OVER_LIMIT_BYTES = MAX_FETCH_BYTES + 1;

    private static final int HTTP_OK = 200;

    private static final int HTTP_NOT_FOUND = 404;

    private static final int HTTP_FOUND = 302;

    private static final String COVER_PATH = "/b/id/1-L.jpg";

    private static final String REDIRECT_PATH = "/cdn/1-L.jpg";

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String LOCATION_HEADER = "Location";

    private static final String JPEG_TYPE = "image/jpeg";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private WireMockServer wireMock;

    private CoverFetchClient client;

    @BeforeAll
    void startServer() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        final CoverFetchProperties properties =
            new CoverFetchProperties(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, MAX_FETCH_BYTES);
        client = new CoverFetchClient(
            new CoverFetchWebClientConfig(properties).coverFetchWebClient(), allowAllGuard());
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @AfterAll
    void stopServer() {
        wireMock.stop();
    }

    private String baseUrl() {
        return "http://localhost:" + wireMock.port();
    }

    private static CoverUrlGuard allowAllGuard() {
        return new CoverUrlGuard() {
            @Override
            public boolean isAllowed(final String url) {
                return true;
            }
        };
    }

    @Test
    @DisplayName("a 200 returns the body and content type")
    void returnsBodyAndContentType() {
        wireMock.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(aResponse()
            .withStatus(HTTP_OK)
            .withHeader(CONTENT_TYPE_HEADER, JPEG_TYPE)
            .withBody(JPEG)));

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
        wireMock.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("a 302 redirect is followed to the body, as OpenLibrary covers do")
    void followsRedirectToBody() {
        wireMock.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(aResponse()
            .withStatus(HTTP_FOUND)
            .withHeader(LOCATION_HEADER, baseUrl() + REDIRECT_PATH)));
        wireMock.stubFor(get(urlPathEqualTo(REDIRECT_PATH)).willReturn(aResponse()
            .withStatus(HTTP_OK)
            .withHeader(CONTENT_TYPE_HEADER, JPEG_TYPE)
            .withBody(JPEG)));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).get().extracting(FetchedImage::bytes).isEqualTo(JPEG);
    }

    @Test
    @DisplayName("a cover past the sources' default decode buffer still downloads")
    void coverPastDefaultBufferDownloads() {
        wireMock.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(aResponse()
            .withStatus(HTTP_OK)
            .withHeader(CONTENT_TYPE_HEADER, JPEG_TYPE)
            .withBody(new byte[PAST_DEFAULT_BUFFER_BYTES])));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched)
            .as("the cover client's own byte limit applies, not the shared source default")
            .get()
            .satisfies(image -> assertThat(image.bytes()).hasSize(PAST_DEFAULT_BUFFER_BYTES));
    }

    @Test
    @DisplayName("a cover over the configured byte limit resolves to empty")
    void coverOverLimitIsEmpty() {
        wireMock.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(aResponse()
            .withStatus(HTTP_OK)
            .withHeader(CONTENT_TYPE_HEADER, JPEG_TYPE)
            .withBody(new byte[OVER_LIMIT_BYTES])));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched)
            .as("an oversized cover is skipped, it must not error the request")
            .isEmpty();
    }

    @Test
    @DisplayName("a redirect loop past the hop limit resolves to empty")
    void redirectLoopIsBounded() {
        wireMock.stubFor(get(urlPathEqualTo(COVER_PATH)).willReturn(aResponse()
            .withStatus(HTTP_FOUND)
            .withHeader(LOCATION_HEADER, baseUrl() + COVER_PATH)));

        final Optional<FetchedImage> fetched = client.fetch(baseUrl() + COVER_PATH);

        assertThat(fetched).isEmpty();
    }
}
