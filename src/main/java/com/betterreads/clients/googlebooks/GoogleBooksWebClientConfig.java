package com.betterreads.clients.googlebooks;

import com.betterreads.clients.http.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds the Google Books {@code WebClient}.
 *
 * <p>{@code WebClient} prints its default URI in debug logs, so the API key goes on each request
 * through a filter.
 */
@Configuration
class GoogleBooksWebClientConfig {

    private static final String API_KEY_PARAM = "key";

    private final GoogleBooksProperties properties;

    GoogleBooksWebClientConfig(final GoogleBooksProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient googleBooksWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, WebClients.USER_AGENT)
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
