package com.betterreads.catalog.service.source;

import java.util.Optional;

import com.betterreads.catalog.image.CoverVersion;
import com.betterreads.common.util.LogSanitizer;
import com.betterreads.integration.minio.ImageStore;
import com.betterreads.integration.minio.ImageStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Mirrors a book's external cover into object storage and returns the stored object key.
 *
 * <p>The fetched bytes are re-encoded to a clean JPEG by {@link CoverImageProcessor}, which rejects
 * anything that is not a decodable image, so an HTML error page or a disguised payload is dropped
 * rather than served later as a cover. An oversized download is rejected before decoding. An
 * already-stored cover is left in place. A fetch, decode, or storage failure leaves the book
 * un-mirrored and returns empty without throwing, so the read path keeps falling back to the
 * external URL.
 */
@Service
public class CoverMirrorService {

    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(CoverMirrorService.class);

    private static final String KEY_PREFIX = "covers/";

    private final ImageStore store;

    private final CoverFetcher fetcher;

    private final CoverImageProcessor processor;

    public CoverMirrorService(
        final ImageStore store, final CoverFetcher fetcher, final CoverImageProcessor processor) {
        this.store = store;
        this.fetcher = fetcher;
        this.processor = processor;
    }

    /**
     * Returns the object key for a book's cover, scoped to the dedup key and versioned by the cover
     * URL. A changed cover URL yields a new key, so the stored bytes a URL maps to never change and
     * the old object is bypassed instead of served stale.
     */
    public static String objectKey(final String dedupKey, final String coverUrl) {
        return KEY_PREFIX + dedupKey + "/" + CoverVersion.of(coverUrl);
    }

    /**
     * Mirrors the cover at {@code coverUrl} and returns its stored object key, or empty when it could
     * not be stored. An already-mirrored cover returns its key without re-fetching.
     */
    public Optional<String> mirror(final String dedupKey, final String coverUrl) {
        final String key = objectKey(dedupKey, coverUrl);
        try {
            if (store.exists(key)) {
                return Optional.of(key);
            }
            return fetcher.fetch(coverUrl)
                .filter(CoverMirrorService::withinSizeCap)
                .flatMap(image -> processor.toCleanJpeg(image.bytes()))
                .map(jpeg -> storeJpeg(key, jpeg));
        } catch (WebClientException | ImageStoreException ex) {
            LOG.warn("catalog.cover-mirror failed for dedupKey={} ({}), leaving external url",
                LogSanitizer.forLog(dedupKey), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String storeJpeg(final String key, final byte[] jpeg) {
        store.put(key, jpeg, MediaType.IMAGE_JPEG_VALUE);
        return key;
    }

    private static boolean withinSizeCap(final FetchedImage image) {
        final int length = image.bytes().length;
        return length > 0 && length <= MAX_IMAGE_BYTES;
    }
}
