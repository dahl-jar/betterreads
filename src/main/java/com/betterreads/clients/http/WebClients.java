package com.betterreads.clients.http;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.betterreads.logging.LogSanitizer;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.netty.http.client.HttpClient;

/** Builds {@code WebClient} builders with explicit connect and response timeouts, and runs 4xx-tolerant GETs. */
public final class WebClients {

    public static final String USER_AGENT =
        "BetterReads/0.1 (https://betterreadsapp.com; book-tracking-app)";

    /**
     * A single Hardcover series enumeration or Wikidata entity document runs to a few hundred
     * kilobytes, past the 256 KB default decode buffer, so the in-memory limit is raised to 4 MB
     * for every source.
     */
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private WebClients() {
    }

    public static WebClient.Builder builderWithTimeouts(
        final String baseUrl,
        final int connectTimeoutMillis,
        final int readTimeoutMillis
    ) {
        final HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
            .responseTimeout(Duration.ofMillis(readTimeoutMillis));

        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES));
    }

    public static Optional<String> getBodyOrEmptyOn4xx(
        final WebClient client,
        final Function<UriBuilder, URI> uri,
        final Logger log,
        final String source
    ) {
        return emptyOn4xx(() -> Optional.ofNullable(client.get()
            .uri(uri)
            .retrieve()
            .bodyToMono(String.class)
            .block()), log, source);
    }

    public static <T> Optional<T> emptyOn4xx(
        final Supplier<Optional<T>> request,
        final Logger log,
        final String source
    ) {
        try {
            return request.get();
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                log.debug("{} returned 4xx status={}",
                    LogSanitizer.forLog(source), exception.getStatusCode().value());
                return Optional.empty();
            }
            throw exception;
        }
    }
}
