package com.betterreads.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.betterreads.catalog.service.read.CoverImageService;
import com.betterreads.catalog.image.store.StoredImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The cover endpoint serves stored bytes with caching headers, answers a matching
 * {@code If-None-Match} with 304, re-sends on a stale ETag, and 404s when no cover resolves.
 */
class ImageControllerTest {

    private static final String KEY = "OL1W";

    private static final String JPEG_TYPE = "image/jpeg";

    private static final byte[] JPEG = "fake-jpeg".getBytes(StandardCharsets.UTF_8);

    private final CoverImageService coverImageService = mock(CoverImageService.class);

    private final ImageController controller = new ImageController(coverImageService);

    @Test
    @DisplayName("a stored cover is served with content type and a long cache header")
    void servesStoredCover() {
        when(coverImageService.loadCover(KEY)).thenReturn(Optional.of(new StoredImage(JPEG, JPEG_TYPE)));

        final ResponseEntity<byte[]> response = controller.cover(KEY, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(JPEG);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo(JPEG_TYPE);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=");
        assertThat(response.getHeaders().getETag()).isNotBlank();
    }

    @Test
    @DisplayName("a request whose If-None-Match matches the current cover gets 304")
    void notModifiedWhenETagMatches() {
        when(coverImageService.loadCover(KEY)).thenReturn(Optional.of(new StoredImage(JPEG, JPEG_TYPE)));
        final ResponseEntity<byte[]> firstRead = controller.cover(KEY, null);
        final String etag = firstRead.getHeaders().getETag();

        final ResponseEntity<byte[]> response = controller.cover(KEY, etag);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("a request holding the ETag of a superseded cover gets the current bytes")
    void servesCoverWhenETagIsStale() {
        when(coverImageService.loadCover(KEY)).thenReturn(Optional.of(new StoredImage(JPEG, JPEG_TYPE)));

        final ResponseEntity<byte[]> response = controller.cover(KEY, "\"an-older-cover\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(JPEG);
    }

    @Test
    @DisplayName("an unresolvable cover returns 404")
    void notFoundWhenNoCover() {
        when(coverImageService.loadCover(KEY)).thenReturn(Optional.empty());

        final ResponseEntity<byte[]> response = controller.cover(KEY, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
