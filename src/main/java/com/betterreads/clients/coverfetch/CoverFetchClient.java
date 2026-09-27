package com.betterreads.clients.coverfetch;

import java.net.URI;
import java.util.Optional;

import com.betterreads.clients.http.WebClients;
import com.betterreads.images.CoverFetcher;
import com.betterreads.images.Image;
import com.betterreads.logging.LogSanitizer;
import org.jspecify.annotations.Nullable;
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
 * checks every hop. A 4xx, an unsafe target, too many hops, or a body over
 * {@code cover-fetch.max-bytes} resolves to empty. 5xx and network errors propagate.
 */
@Component
class CoverFetchClient implements CoverFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CoverFetchClient.class);

    private static final int MAX_REDIRECTS = 3;

    private final WebClient coverFetchWebClient;

    private final CoverUrlGuard urlGuard;

    CoverFetchClient(final WebClient coverFetchWebClient, final CoverUrlGuard urlGuard) {
        this.coverFetchWebClient = coverFetchWebClient;
        this.urlGuard = urlGuard;
    }

    @Override
    public Optional<Image> fetch(final String url) {
        String target = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!urlGuard.isAllowed(target)) {
                LOG.warn("image.fetch refused unsafe cover url={}", LogSanitizer.forLog(target));
                return Optional.empty();
            }
            final Hop result = requestOnce(target);
            if (result.location() == null) {
                return result.image();
            }
            target = result.location();
        }
        LOG.debug("image.fetch exceeded redirect limit url={}", LogSanitizer.forLog(url));
        return Optional.empty();
    }

    private Hop requestOnce(final String url) {
        try {
            return WebClients.emptyOn4xx(() -> Optional.of(toHop(coverFetchWebClient.get()
                    .uri(URI.create(url))
                    .exchangeToMono(client -> client.toEntity(byte[].class))
                    .block())), LOG, "image.fetch url=" + url)
                .orElseGet(() -> Hop.done(Optional.empty()));
        } catch (DataBufferLimitException oversized) {
            LOG.warn("image.fetch cover body over the download limit, skipped url={}",
                LogSanitizer.forLog(url));
            return Hop.done(Optional.empty());
        }
    }

    private static Hop toHop(final @Nullable ResponseEntity<byte[]> response) {
        if (response == null) {
            return Hop.done(Optional.empty());
        }
        if (response.getStatusCode().is3xxRedirection()) {
            return Hop.redirect(response.getHeaders().getFirst("Location"));
        }
        final byte[] body = response.getBody();
        if (response.getStatusCode().is2xxSuccessful() && body != null) {
            return Hop.done(Optional.of(new Image(body, contentType(response))));
        }
        return Hop.done(Optional.empty());
    }

    private static String contentType(final ResponseEntity<byte[]> response) {
        final MediaType type = response.getHeaders().getContentType();
        return type == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : type.toString();
    }

    private record Hop(Optional<Image> image, @Nullable String location) {

        static Hop done(final Optional<Image> image) {
            return new Hop(image, null);
        }

        static Hop redirect(final @Nullable String location) {
            return new Hop(Optional.empty(), location);
        }
    }
}
