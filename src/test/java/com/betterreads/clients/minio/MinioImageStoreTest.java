package com.betterreads.clients.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Optional;

import com.betterreads.images.Image;
import com.betterreads.images.ImageStoreException;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinioImageStoreTest {

    private static final String MINIO_IMAGE =
        "ghcr.io/dahl-jar/minio@sha256:52dfd5c0bbd38d3219f2058c7af216d9f9a27a994b7b5baad09bbd38866015ff";

    private static final int API_PORT = 9000;

    private static final String CREDENTIAL = "minioadmin";

    private static final String BUCKET = "test-images";

    private static final String IMAGE_KEY = "covers/OL1W";

    private static final String OTHER_KEY = "covers/OL2W";

    private static final String CONTENT_TYPE = "image/jpeg";

    private static final byte[] IMAGE_BYTES = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);

    private final GenericContainer<?> minio =
        new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
            .withEnv("MINIO_ROOT_USER", CREDENTIAL)
            .withEnv("MINIO_ROOT_PASSWORD", CREDENTIAL)
            .withCommand("server", "/data")
            .withExposedPorts(API_PORT);

    private MinioClient client;

    private String endpoint;

    private MinioImageStore store;

    @BeforeAll
    void startMinio() throws MinioException, GeneralSecurityException, IOException {
        minio.start();
        endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(API_PORT);
        client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(CREDENTIAL, CREDENTIAL)
            .build();
        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        store = new MinioImageStore(client, new MinioProperties(
            endpoint, BUCKET, CREDENTIAL, CREDENTIAL));
    }

    @AfterAll
    void stopMinio() {
        minio.stop();
    }

    @Test
    void shouldThrowWhenBucketIsMissing() {
        final MinioImageStore missingBucketStore = missingBucketStore();

        assertThatThrownBy(() -> missingBucketStore.get(IMAGE_KEY)).isInstanceOf(ImageStoreException.class);
    }

    @Test
    void shouldThrowWhenPutHitsMissingBucket() {
        final MinioImageStore missingBucketStore = missingBucketStore();

        assertThatThrownBy(() -> missingBucketStore.put(IMAGE_KEY, IMAGE_BYTES, CONTENT_TYPE))
            .isInstanceOf(ImageStoreException.class);
    }

    @Test
    void shouldThrowWhenExistsHitsMissingBucket() {
        final MinioImageStore missingBucketStore = missingBucketStore();

        assertThatThrownBy(() -> missingBucketStore.exists(IMAGE_KEY)).isInstanceOf(ImageStoreException.class);
    }

    @Test
    @DisplayName("a stored object round-trips its bytes and content type")
    void putThenGetRoundTrips() {
        store.put(IMAGE_KEY, IMAGE_BYTES, CONTENT_TYPE);

        final Optional<Image> fetched = store.get(IMAGE_KEY);

        assertThat(fetched).get()
            .satisfies(image -> {
                assertThat(image.bytes()).isEqualTo(IMAGE_BYTES);
                assertThat(image.contentType()).isEqualTo(CONTENT_TYPE);
            });
    }

    @Test
    @DisplayName("an absent key resolves to empty")
    void absentKeyIsEmpty() {
        final Optional<Image> fetched = store.get("covers/does-not-exist");

        assertThat(fetched).isEmpty();
    }

    @Test
    void shouldReportWrittenKeyAsExisting() {
        store.put(OTHER_KEY, IMAGE_BYTES, CONTENT_TYPE);

        final boolean exists = store.exists(OTHER_KEY);

        assertThat(exists).isTrue();
    }

    @Test
    void shouldReportUnwrittenKeyAsMissing() {
        final boolean exists = store.exists("covers/never-written");

        assertThat(exists).isFalse();
    }

    private MinioImageStore missingBucketStore() {
        return new MinioImageStore(client, new MinioProperties(endpoint, "never-created", CREDENTIAL, CREDENTIAL));
    }
}
