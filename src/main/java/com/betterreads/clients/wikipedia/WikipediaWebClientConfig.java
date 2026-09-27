package com.betterreads.clients.wikipedia;

import com.betterreads.clients.http.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wikipedia WebClient with connect and response timeouts.
 *
 * <p>Wikimedia APIs require a {@code User-Agent} that names the app and a contact URL.
 */
@Configuration
public class WikipediaWebClientConfig {

    private final WikipediaProperties properties;

    public WikipediaWebClientConfig(final WikipediaProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient wikipediaWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, WebClients.USER_AGENT)
            .build();
    }
}
