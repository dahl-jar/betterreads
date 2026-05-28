package com.betterreads.integration.openlibrary;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the OpenLibrary {@link WebClient} with explicit connect and response timeouts.
 *
 * <p>The {@code User-Agent} carries the configured contact email because OpenLibrary asks callers
 * to identify themselves and throttles anonymous traffic.
 */
@Configuration
public class OpenLibraryWebClientConfig {

    private final OpenLibraryProperties properties;

    public OpenLibraryWebClientConfig(final OpenLibraryProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient openLibraryWebClient() {
        final HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeout())
            .responseTimeout(Duration.ofMillis(properties.readTimeout()));

        return WebClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    private String userAgent() {
        return "BetterReads/0.1 (book-tracking-app; " + properties.contactEmail() + ")";
    }
}
