package com.betterreads.integration.image;

import java.net.URI;
import java.util.Optional;

import com.betterreads.catalog.service.source.CoverFetcher;
import com.betterreads.catalog.service.source.FetchedImage;
import com.betterreads.common.util.LogSanitizer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Downloads cover bytes from an external URL with explicit timeouts.
 *
 * <p>Redirects are followed manually, with each hop's target re-checked by {@link CoverUrlGuard}, so
 * an OpenLibrary cover that 302s to its CDN still resolves while a redirect to a private address is
 * refused. A 4xx, an unsafe target, too many hops, or a body over {@code cover-fetch.max-bytes}
 * resolves to empty; 5xx and network errors propagate as {@link
 * org.springframework.web.reactive.function.client.WebClientException} for the caller to skip.
 */
@Component
public class CoverFetchClient implements CoverFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CoverFetchClient.class);

    private static final int MAX_REDIRECTS = 3;

    private final WebClient coverFetchWebClient;

    private final CoverUrlGuard urlGuard;

    public CoverFetchClient(final WebClient coverFetchWebClient, final CoverUrlGuard urlGuard) {
        this.coverFetchWebClient = coverFetchWebClient;
        this.urlGuard = urlGuard;
    }

    @Override
    public Optional<FetchedImage> fetch(final String url) {
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
            return interpret(coverFetchWebClient.get()
                .uri(URI.create(url))
                .exchangeToMono(client -> client.toEntity(byte[].class))
                .block());
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                LOG.debug("image.fetch returned 4xx status={} url={}",
                    ex.getStatusCode().value(), LogSanitizer.forLog(url));
                return Hop.done(Optional.empty());
            }
            throw ex;
        } catch (DataBufferLimitException oversized) {
            LOG.warn("image.fetch cover body over the download limit, skipped url={}",
                LogSanitizer.forLog(url));
            return Hop.done(Optional.empty());
        }
    }

    private static Hop interpret(final @Nullable ResponseEntity<byte[]> response) {
        if (response == null) {
            return Hop.done(Optional.empty());
        }
        if (response.getStatusCode().is3xxRedirection()) {
            return Hop.redirect(response.getHeaders().getFirst("Location"));
        }
        final byte[] body = response.getBody();
        if (response.getStatusCode().is2xxSuccessful() && body != null) {
            return Hop.done(Optional.of(new FetchedImage(body, contentType(response))));
        }
        return Hop.done(Optional.empty());
    }

    private static String contentType(final ResponseEntity<byte[]> response) {
        final MediaType type = response.getHeaders().getContentType();
        return type == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : type.toString();
    }

    private record Hop(Optional<FetchedImage> image, @Nullable String location) {

        static Hop done(final Optional<FetchedImage> image) {
            return new Hop(image, null);
        }

        static Hop redirect(final @Nullable String location) {
            return new Hop(Optional.empty(), location);
        }
    }
}
