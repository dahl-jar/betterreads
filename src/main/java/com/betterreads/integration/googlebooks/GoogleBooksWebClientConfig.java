package com.betterreads.integration.googlebooks;

import com.betterreads.common.web.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds the Google Books {@link WebClient} with explicit connect and response timeouts.
 *
 * <p>The API key is appended on every outbound request by an exchange filter. It is kept out of the
 * default URI, which {@code WebClient} prints in debug logs on construction.
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
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
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
