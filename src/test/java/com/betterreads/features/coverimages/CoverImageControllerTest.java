package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverBackfillServiceTest.KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.betterreads.images.Image;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CoverImageControllerTest {

    private static final String JPEG_TYPE = "image/jpeg";

    private static final byte[] JPEG = "fake-jpeg".getBytes(StandardCharsets.UTF_8);

    private static final byte[] NEW_JPEG = "new-fake-jpeg".getBytes(StandardCharsets.UTF_8);

    private final CoverImageService coverImageService = mock(CoverImageService.class);

    private final CoverImageController controller = new CoverImageController(coverImageService);

    @Test
    @DisplayName("a stored cover is served with content type and a long cache header")
    void servesStoredCover() {
        when(coverImageService.loadCover(KEY)).thenReturn(Optional.of(new Image(JPEG, JPEG_TYPE)));

        final ResponseEntity<byte[]> response = controller.cover(KEY, null);

        assertThat(response).satisfies(
            r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK),
            r -> assertThat(r.getBody()).isEqualTo(JPEG),
            r -> assertThat(r.getHeaders().getContentType()).hasToString(JPEG_TYPE),
            r -> assertThat(r.getHeaders().getCacheControl()).isEqualTo("max-age=31536000, public, immutable"));
    }

    @Test
    @DisplayName("a request whose If-None-Match matches the current cover gets 304")
    void notModifiedWhenETagMatches() {
        when(coverImageService.loadCover(KEY)).thenReturn(Optional.of(new Image(JPEG, JPEG_TYPE)));
        final ResponseEntity<byte[]> firstRead = controller.cover(KEY, null);
        final String etag = firstRead.getHeaders().getETag();

        final ResponseEntity<byte[]> response = controller.cover(KEY, etag);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("a request holding the ETag of a superseded cover gets the current bytes")
    void servesCoverWhenETagIsStale() {
        when(coverImageService.loadCover(KEY))
            .thenReturn(Optional.of(new Image(JPEG, JPEG_TYPE)))
            .thenReturn(Optional.of(new Image(NEW_JPEG, JPEG_TYPE)));
        final ResponseEntity<byte[]> oldRead = controller.cover(KEY, null);
        final String oldEtag = oldRead.getHeaders().getETag();

        final ResponseEntity<byte[]> response = controller.cover(KEY, oldEtag);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(NEW_JPEG);
    }

    @Test
    @DisplayName("an unresolvable cover returns 404")
    void notFoundWhenNoCover() {
        when(coverImageService.loadCover(KEY)).thenReturn(Optional.empty());

        final ResponseEntity<byte[]> response = controller.cover(KEY, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
