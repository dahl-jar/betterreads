package com.betterreads.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the OpenLibrary {@link WebClient} with explicit connect and response timeouts.
 */
@Configuration
public class WebClientConfig {

    private static final String USER_AGENT_VALUE = "BetterReads/0.1 (book-tracking-app)";

    private final String baseUrl;

    private final int connectTimeout;

    private final int readTimeout;

    public WebClientConfig(
            @Value("${openlibrary.base-url}") final String baseUrl,
            @Value("${openlibrary.connect-timeout}") final int connectTimeout,
            @Value("${openlibrary.read-timeout}") final int readTimeout) {
        this.baseUrl = baseUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Bean
    WebClient openLibraryWebClient() {
        final HttpClient httpClient = HttpClient.create()
            .option(
                io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                connectTimeout)
            .responseTimeout(Duration.ofMillis(readTimeout));

        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
