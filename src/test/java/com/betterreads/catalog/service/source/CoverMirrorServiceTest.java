package com.betterreads.catalog.service.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.betterreads.integration.minio.ImageStore;
import com.betterreads.integration.minio.StoredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The mirror service fetches a book's external cover, re-encodes it to a clean JPEG, and stores the
 * bytes under a key derived from the dedup key. A non-image, an oversized download, or a failed fetch
 * leaves the book un-mirrored and never throws into the caller.
 */
class CoverMirrorServiceTest {

    private static final int IMAGE_DIMENSION = 200;

    private static final String DEDUP_KEY = "OL1W";

    private static final String COVER_URL = "https://covers.example.org/b/id/1-L.jpg";

    private static final String PNG_TYPE = "image/png";

    private static final byte[] PNG = pngOf(IMAGE_DIMENSION);

    private final RecordingImageStore store = new RecordingImageStore();

    private Optional<FetchedImage> fetchResult = Optional.empty();

    private CoverMirrorService service;

    @BeforeEach
    void setUp() {
        service = new CoverMirrorService(store, url -> fetchResult, new CoverImageProcessor());
    }

    @Test
    @DisplayName("a valid image is re-encoded to jpeg and stored under the url-versioned object key")
    void storesValidImage() {
        fetchResult = Optional.of(new FetchedImage(PNG, PNG_TYPE));
        final String expectedKey = CoverMirrorService.objectKey(DEDUP_KEY, COVER_URL);

        final Optional<String> key = service.mirror(DEDUP_KEY, COVER_URL);

        assertThat(key).contains(expectedKey);
        assertThat(store.saved.get(expectedKey).contentType()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
    }

    @Test
    @DisplayName("the object key is scoped to the dedup key and changes when the cover url changes")
    void objectKeyVersionsOnUrl() {
        final String first = CoverMirrorService.objectKey(DEDUP_KEY, COVER_URL);
        final String second = CoverMirrorService.objectKey(DEDUP_KEY, "https://covers.example.org/2.jpg");

        assertThat(first)
            .startsWith("covers/" + DEDUP_KEY + "/")
            .isNotEqualTo(second);
    }

    @Test
    @DisplayName("an already-mirrored cover is not re-fetched or re-stored")
    void skipsAlreadyMirrored() {
        final String objectKey = CoverMirrorService.objectKey(DEDUP_KEY, COVER_URL);
        store.saved.put(objectKey, new StoredImage(PNG, MediaType.IMAGE_JPEG_VALUE));
        fetchResult = Optional.of(new FetchedImage(PNG, PNG_TYPE));

        final Optional<String> key = service.mirror(DEDUP_KEY, COVER_URL);

        assertThat(key).contains(objectKey);
        assertThat(store.saved.get(objectKey).bytes()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("bytes that do not decode as an image are rejected and nothing is stored")
    void rejectsNonImage() {
        fetchResult = Optional.of(
            new FetchedImage("<html>404</html>".getBytes(StandardCharsets.UTF_8), "text/html"));

        final Optional<String> key = service.mirror(DEDUP_KEY, COVER_URL);

        assertThat(key).isEmpty();
        assertThat(store.saved).isEmpty();
    }

    @Test
    @DisplayName("an oversize download is rejected before decoding")
    void rejectsOversize() {
        final byte[] huge = new byte[CoverMirrorService.MAX_IMAGE_BYTES + 1];
        fetchResult = Optional.of(new FetchedImage(huge, "image/jpeg"));

        final Optional<String> key = service.mirror(DEDUP_KEY, COVER_URL);

        assertThat(key).isEmpty();
        assertThat(store.saved).isEmpty();
    }

    @Test
    @DisplayName("a failed fetch leaves the book un-mirrored without throwing")
    void emptyFetchIsSkipped() {
        fetchResult = Optional.empty();

        final Optional<String> key = service.mirror(DEDUP_KEY, COVER_URL);

        assertThat(key).isEmpty();
        assertThat(store.saved).isEmpty();
    }

    private static byte[] pngOf(final int dimension) {
        final BufferedImage image = new BufferedImage(dimension, dimension, BufferedImage.TYPE_INT_RGB);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    private static final class RecordingImageStore implements ImageStore {
        private final Map<String, StoredImage> saved = new HashMap<>();

        @Override
        public Optional<StoredImage> get(final String key) {
            return Optional.ofNullable(saved.get(key));
        }

        @Override
        public void put(final String key, final byte[] bytes, final String contentType) {
            saved.put(key, new StoredImage(bytes, contentType));
        }

        @Override
        public boolean exists(final String key) {
            return saved.containsKey(key);
        }
    }
}
