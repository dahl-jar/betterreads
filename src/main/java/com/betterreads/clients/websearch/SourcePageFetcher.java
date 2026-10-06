package com.betterreads.clients.websearch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.betterreads.clients.http.PublicUrlGuard;
import com.betterreads.clients.http.Redirects;
import com.betterreads.clients.http.WebClients;
import com.betterreads.logging.LogSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

@Component
class SourcePageFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(SourcePageFetcher.class);

    private final WebClient sourcePageWebClient;

    private final PublicUrlGuard urlGuard;

    private final WebSearchProperties properties;

    SourcePageFetcher(
        final WebClient sourcePageWebClient, final PublicUrlGuard urlGuard, final WebSearchProperties properties) {
        this.sourcePageWebClient = sourcePageWebClient;
        this.urlGuard = urlGuard;
        this.properties = properties;
    }

    Optional<String> text(final String url) {
        return page(url).map(SourcePage::text);
    }

    Optional<SourcePage> page(final String url) {
        return Redirects.follow(url, this::allowed, this::requestOnce);
    }

    private boolean allowed(final String target) {
        final boolean allowed = hasAllowedScheme(target)
            && SourceHosts.isListed(target, properties.allowedDomains())
            && urlGuard.isAllowed(target);
        if (!allowed) {
            LOG.info("catalog.metadata-check page refused url={}", LogSanitizer.forLog(target));
        }
        return allowed;
    }

    private boolean hasAllowedScheme(final String target) {
        try {
            final String scheme = URI.create(target).getScheme();
            return scheme != null && properties.pageSchemes().contains(scheme.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Redirects.Hop<SourcePage> requestOnce(final String url) {
        try {
            final ResponseEntity<byte[]> response =
                WebClients.getBytes(sourcePageWebClient, url, Duration.ofMillis(properties.pageReadTimeout()));
            return Redirects.Hop.of(response, ok -> parse(Objects.requireNonNull(ok.getBody()), url));
        } catch (WebClientException | IllegalArgumentException | IllegalStateException ex) {
            LOG.info("catalog.metadata-check page failed url={} ({})", LogSanitizer.forLog(url),
                ex.getClass().getSimpleName());
            return Redirects.Hop.done(Optional.empty());
        }
    }

    private static Optional<SourcePage> parse(final byte[] body, final String url) {
        try {
            final Document document = Jsoup.parse(new ByteArrayInputStream(body), null, url);
            final String heading = (document.title() + " " + document.select("h1").text()).strip();
            return Optional.of(document.body().text())
                .filter(text -> !text.isBlank())
                .map(text -> new SourcePage(url, heading, text));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }
}
