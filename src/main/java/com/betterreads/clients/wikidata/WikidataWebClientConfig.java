package com.betterreads.clients.wikidata;

import com.betterreads.clients.http.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wikidata WebClient with connect and response timeouts.
 *
 * <p>Wikimedia APIs require a {@code User-Agent} that names the app and a contact URL.
 */
@Configuration
public class WikidataWebClientConfig {

    private final WikidataProperties properties;

    public WikidataWebClientConfig(final WikidataProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient wikidataWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, WebClients.USER_AGENT)
            .build();
    }
}
