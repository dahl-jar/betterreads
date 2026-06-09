package com.betterreads.integration.minio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Round-trips bytes through {@link MinioImageStore} against a real MinIO container: a stored object
 * comes back with its bytes and content type, an absent key resolves to empty, and {@code exists}
 * reflects what was written.
 */
// PMD.SignatureDeclareThrowsException + PMD.CloseResource: the MinIO client lives for the test-class lifecycle
@SuppressWarnings({"PMD.SignatureDeclareThrowsException", "PMD.CloseResource"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinioImageStoreTest {

    private static final int API_PORT = 9000;

    private static final String CREDENTIAL = "minioadmin";

    private static final String BUCKET = "betterreads-images";

    private static final String IMAGE_KEY = "covers/OL1W";

    private static final String OTHER_KEY = "covers/OL2W";

    private static final String CONTENT_TYPE = "image/jpeg";

    private static final byte[] IMAGE_BYTES = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);

    @SuppressWarnings("resource")
    private final GenericContainer<?> minio =
        new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2024-01-16T16-07-38Z"))
            .withEnv("MINIO_ROOT_USER", CREDENTIAL)
            .withEnv("MINIO_ROOT_PASSWORD", CREDENTIAL)
            .withCommand("server", "/data")
            .withExposedPorts(API_PORT);

    private MinioImageStore store;

    @BeforeAll
    void startMinio() throws Exception {
        minio.start();
        final String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(API_PORT);
        final MinioClient client = MinioClient.builder()
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
    @DisplayName("a stored object round-trips its bytes and content type")
    void putThenGetRoundTrips() {
        store.put(IMAGE_KEY, IMAGE_BYTES, CONTENT_TYPE);

        final Optional<StoredImage> fetched = store.get(IMAGE_KEY);

        assertThat(fetched).get()
            .satisfies(image -> {
                assertThat(image.bytes()).isEqualTo(IMAGE_BYTES);
                assertThat(image.contentType()).isEqualTo(CONTENT_TYPE);
            });
    }

    @Test
    @DisplayName("an absent key resolves to empty")
    void absentKeyIsEmpty() {
        final Optional<StoredImage> fetched = store.get("covers/does-not-exist");

        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("exists reflects whether the object was written")
    void existsReflectsState() {
        store.put(OTHER_KEY, IMAGE_BYTES, CONTENT_TYPE);

        assertThat(store.exists(OTHER_KEY)).isTrue();
        assertThat(store.exists("covers/never-written")).isFalse();
    }
}
