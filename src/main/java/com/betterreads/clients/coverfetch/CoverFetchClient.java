package com.betterreads.clients.coverfetch;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.betterreads.clients.http.PublicUrlGuard;
import com.betterreads.clients.http.Redirects;
import com.betterreads.clients.http.WebClients;
import com.betterreads.images.CoverFetcher;
import com.betterreads.images.Image;
import com.betterreads.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Downloads cover images from external URLs.
 *
 * <p>OpenLibrary covers 302 to a CDN, so redirects are followed one hop at a time and the URL guard
 * checks every hop. A non-2xx answer, an unsafe target, too many hops, a timeout, or a body over
 * {@code cover-fetch.max-bytes} resolves to empty. Network errors propagate.
 */
@Component
class CoverFetchClient implements CoverFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CoverFetchClient.class);

    private final WebClient coverFetchWebClient;

    private final PublicUrlGuard urlGuard;

    private final CoverFetchProperties properties;

    CoverFetchClient(
        final WebClient coverFetchWebClient, final PublicUrlGuard urlGuard, final CoverFetchProperties properties) {
        this.coverFetchWebClient = coverFetchWebClient;
        this.urlGuard = urlGuard;
        this.properties = properties;
    }

    @Override
    public Optional<Image> fetch(final String url) {
        return Redirects.follow(url, this::allowed, this::requestOnce);
    }

    private boolean allowed(final String target) {
        final boolean allowed = urlGuard.isAllowed(target);
        if (!allowed) {
            LOG.warn("image.fetch refused unsafe cover url={}", LogSanitizer.forLog(target));
        }
        return allowed;
    }

    private Redirects.Hop<Image> requestOnce(final String url) {
        try {
            final ResponseEntity<byte[]> response =
                WebClients.getBytes(coverFetchWebClient, url, Duration.ofMillis(properties.readTimeout()));
            return Redirects.Hop.of(response,
                ok -> Optional.of(new Image(Objects.requireNonNull(ok.getBody()), contentType(ok))));
        } catch (DataBufferLimitException oversized) {
            LOG.warn("image.fetch cover body over the download limit, skipped url={}",
                LogSanitizer.forLog(url));
            return Redirects.Hop.done(Optional.empty());
        } catch (IllegalStateException timedOut) {
            LOG.warn("image.fetch cover download timed out, skipped url={}", LogSanitizer.forLog(url));
            return Redirects.Hop.done(Optional.empty());
        }
    }

    private static String contentType(final ResponseEntity<byte[]> response) {
        final MediaType type = response.getHeaders().getContentType();
        return type == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : type.toString();
    }
}
