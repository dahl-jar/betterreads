package com.betterreads.integration.minio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Optional;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * {@link ImageStore} backed by MinIO over its S3 API.
 *
 * <p>A missing object resolves to empty rather than throwing, since a not-yet-mirrored cover is an
 * expected state the read path recovers from. Any other failure propagates as {@link
 * ImageStoreException} for the caller to handle or skip.
 */
@Component
public class MinioImageStore implements ImageStore {

    private static final String NO_SUCH_KEY = "NoSuchKey";

    private static final long NO_PART_SIZE = -1;

    private final MinioClient client;

    private final String bucket;

    public MinioImageStore(final MinioClient client, final MinioProperties properties) {
        this.client = client;
        this.bucket = properties.bucket();
    }

    @Override
    public Optional<StoredImage> get(final String key) {
        try (GetObjectResponse response = client.getObject(
            GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            final byte[] bytes = response.readAllBytes();
            final String contentType = response.headers().get(HttpHeaders.CONTENT_TYPE);
            return Optional.of(new StoredImage(bytes, contentType));
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            if (isMissing(ex)) {
                return Optional.empty();
            }
            throw new ImageStoreException(reading(key), ex);
        }
    }

    @Override
    public void put(final String key, final byte[] bytes, final String contentType) {
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .stream(stream, bytes.length, NO_PART_SIZE)
                .contentType(contentType)
                .build());
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            throw new ImageStoreException("writing " + key, ex);
        }
    }

    @Override
    public boolean exists(final String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            if (isMissing(ex)) {
                return false;
            }
            throw new ImageStoreException("stat " + key, ex);
        }
    }

    private static String reading(final String key) {
        return "reading " + key;
    }

    private static boolean isMissing(final Exception ex) {
        return ex instanceof ErrorResponseException error
            && NO_SUCH_KEY.equals(error.errorResponse().code());
    }
}
