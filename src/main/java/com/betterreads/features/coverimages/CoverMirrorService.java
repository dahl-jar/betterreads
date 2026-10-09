package com.betterreads.features.coverimages;

import java.util.Optional;
import java.util.function.Supplier;

import com.betterreads.images.CoverFetcher;
import com.betterreads.images.CoverVersion;
import com.betterreads.images.Image;
import com.betterreads.images.ImageStore;
import com.betterreads.images.ImageStoreException;
import com.betterreads.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Mirrors a book's external cover into object storage.
 *
 * <p>A fetch, decode, or storage failure returns empty, so the book keeps its external URL.
 */
@Service
class CoverMirrorService {

    private static final Logger LOG = LoggerFactory.getLogger(CoverMirrorService.class);

    private static final String KEY_PREFIX = "covers/";

    private final ImageStore store;

    private final CoverFetcher fetcher;

    private final CoverImageProcessor processor;

    CoverMirrorService(
        final ImageStore store, final CoverFetcher fetcher, final CoverImageProcessor processor) {
        this.store = store;
        this.fetcher = fetcher;
        this.processor = processor;
    }

    /** A changed cover URL gets a new key, so the bytes stored under a key never change. */
    public static String objectKey(final String dedupKey, final String coverUrl) {
        return KEY_PREFIX + dedupKey + "/" + CoverVersion.of(coverUrl);
    }

    /** A stored cover is not re-fetched. */
    public Optional<String> mirror(final String dedupKey, final String coverUrl) {
        return storeIfMissing(dedupKey, coverUrl, () -> fetcher.fetch(coverUrl));
    }

    public Optional<String> mirror(final String dedupKey, final String coverUrl, final Image image) {
        return storeIfMissing(dedupKey, coverUrl, () -> Optional.of(image));
    }

    private Optional<String> storeIfMissing(
        final String dedupKey, final String coverUrl, final Supplier<Optional<Image>> fetch) {
        final String key = objectKey(dedupKey, coverUrl);
        try {
            if (store.exists(key)) {
                return Optional.of(key);
            }
            return fetch.get()
                .flatMap(fetched -> processor.toCleanJpeg(fetched.bytes()))
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
}
