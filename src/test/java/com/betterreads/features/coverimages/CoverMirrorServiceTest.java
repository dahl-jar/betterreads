package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.betterreads.images.Image;
import com.betterreads.images.ImageStore;
import com.betterreads.images.ImageStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class CoverMirrorServiceTest {

    private static final int IMAGE_DIMENSION = 200;

    private static final String COVER_URL = "https://covers.example.org/b/id/1-L.jpg";

    private static final String PNG_TYPE = "image/png";

    private static final byte[] PNG = CoverImageFixtures.blank(IMAGE_DIMENSION, IMAGE_DIMENSION);

    private final RecordingImageStore store = new RecordingImageStore();

    private Optional<Image> fetchResult = Optional.empty();

    private CoverMirrorService service;

    @BeforeEach
    void setUp() {
        service = new CoverMirrorService(store, url -> fetchResult, new CoverImageProcessor());
    }

    @Test
    @DisplayName("a valid image is re-encoded to jpeg and stored under the url-versioned object key")
    void storesValidImage() {
        fetchResult = Optional.of(new Image(PNG, PNG_TYPE));
        final String expectedKey = CoverMirrorService.objectKey(KEY, COVER_URL);

        final Optional<String> key = service.mirror(KEY, COVER_URL);

        assertThat(key).contains(expectedKey);
        assertThat(store.saved.get(expectedKey).contentType()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
    }

    @Test
    void shouldStoreAnImageTheCallerFetched() {
        final String expectedKey = CoverMirrorService.objectKey(KEY, COVER_URL);

        final Optional<String> key = service.mirror(KEY, COVER_URL, new Image(PNG, PNG_TYPE));

        assertThat(key).contains(expectedKey);
        assertThat(store.saved).containsKey(expectedKey);
    }

    @Test
    @DisplayName("the object key is scoped to the dedup key and changes when the cover url changes")
    void objectKeyVersionsOnUrl() {
        final String first = CoverMirrorService.objectKey(KEY, COVER_URL);
        final String second = CoverMirrorService.objectKey(KEY, "https://covers.example.org/2.jpg");

        assertThat(first)
            .startsWith("covers/" + KEY + "/")
            .isNotEqualTo(second);
    }

    @Test
    @DisplayName("a mirrored cover is not re-fetched or re-stored")
    void skipsAlreadyMirrored() {
        final String objectKey = CoverMirrorService.objectKey(KEY, COVER_URL);
        store.saved.put(objectKey, new Image(PNG, MediaType.IMAGE_JPEG_VALUE));
        fetchResult = Optional.of(new Image(PNG, PNG_TYPE));

        final Optional<String> key = service.mirror(KEY, COVER_URL);

        assertThat(key).contains(objectKey);
        assertThat(store.saved.get(objectKey).bytes()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("bytes that do not decode as an image are rejected and nothing is stored")
    void rejectsNonImage() {
        fetchResult = Optional.of(
            new Image(CoverImageFixtures.notImage(), "text/html"));

        final Optional<String> key = service.mirror(KEY, COVER_URL);

        assertThat(key).isEmpty();
        assertThat(store.saved).isEmpty();
    }

    @Test
    @DisplayName("a failed fetch leaves the book un-mirrored without throwing")
    void emptyFetchIsSkipped() {
        fetchResult = Optional.empty();

        final Optional<String> key = service.mirror(KEY, COVER_URL);

        assertThat(key).isEmpty();
        assertThat(store.saved).isEmpty();
    }

    @Test
    @DisplayName("a cover host returning 503 leaves the book un-mirrored without throwing")
    void fetchTransportFailureIsContained() {
        final CoverMirrorService failingFetch = new CoverMirrorService(
            store,
            url -> {
                throw new WebClientResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(), HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
            },
            new CoverImageProcessor());

        final Optional<String> key = failingFetch.mirror(KEY, COVER_URL);

        assertThat(key).isEmpty();
        assertThat(store.saved).isEmpty();
    }

    @Test
    @DisplayName("an object store outage leaves the book un-mirrored without throwing")
    void shouldReturnEmptyWhenStoreFails() {
        final ImageStore failingStore = mock(ImageStore.class);
        when(failingStore.exists(anyString()))
            .thenThrow(new ImageStoreException("stat", new IllegalStateException("down")));
        final CoverMirrorService failingMirror =
            new CoverMirrorService(failingStore, url -> fetchResult, new CoverImageProcessor());

        final Optional<String> key = failingMirror.mirror(KEY, COVER_URL);

        assertThat(key).isEmpty();
    }

    private static final class RecordingImageStore implements ImageStore {
        private final Map<String, Image> saved = new HashMap<>();

        @Override
        public Optional<Image> get(final String key) {
            return Optional.ofNullable(saved.get(key));
        }

        @Override
        public void put(final String key, final byte[] bytes, final String contentType) {
            saved.put(key, new Image(bytes, contentType));
        }

        @Override
        public boolean exists(final String key) {
            return saved.containsKey(key);
        }
    }
}
