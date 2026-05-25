package com.betterreads.integration.googlebooks;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the Google Books {@link WebClient} with explicit connect and response timeouts.
 *
 * <p>The API key is appended on every outbound request by an exchange filter rather than
 * baked into a default URI, so the key is not visible in {@code WebClient} debug logs that
 * print the base URI on construction.
 */
@Configuration
public class GoogleBooksWebClientConfig {

    private static final String USER_AGENT_VALUE = "BetterReads/0.1 (book-tracking-app)";

    private static final String API_KEY_PARAM = "key";

    private final GoogleBooksProperties properties;

    public GoogleBooksWebClientConfig(final GoogleBooksProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient googleBooksWebClient() {
        final HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeout())
            .responseTimeout(Duration.ofMillis(properties.readTimeout()));

        return WebClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .filter(apiKeyFilter())
            .build();
    }

    private ExchangeFilterFunction apiKeyFilter() {
        return (request, next) -> {
            final String key = properties.apiKey();
            if (key == null || key.isBlank()) {
                return next.exchange(request);
            }
            final ClientRequest signed = ClientRequest.from(request)
                .url(UriComponentsBuilder.fromUri(request.url())
                    .queryParam(API_KEY_PARAM, key)
                    .build(true)
                    .toUri())
                .build();
            return next.exchange(signed);
        };
    }
}
