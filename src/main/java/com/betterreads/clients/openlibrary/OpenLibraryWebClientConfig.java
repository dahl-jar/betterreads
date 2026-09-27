package com.betterreads.clients.openlibrary;

import com.betterreads.clients.http.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/** OpenLibrary throttles anonymous traffic, so the User-Agent carries the contact email. */
@Configuration
class OpenLibraryWebClientConfig {

    private final OpenLibraryProperties properties;

    OpenLibraryWebClientConfig(final OpenLibraryProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient openLibraryWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT,
                "BetterReads/0.1 (book-tracking-app; " + properties.contactEmail() + ")")
            .build();
    }
}
