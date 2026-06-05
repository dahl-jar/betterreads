package com.betterreads.common.web;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Builds {@link WebClient.Builder} instances with explicit connect and response timeouts. */
public final class WebClients {

    private WebClients() {
    }

    /**
     * Returns a {@link WebClient.Builder} bound to {@code baseUrl} with the given timeouts applied.
     * The caller adds source-specific headers and filters, then {@code build()}s.
     */
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
            .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
