package com.betterreads.integration.openlibrary;

import com.betterreads.common.web.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

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
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent())
            .build();
    }

    private String userAgent() {
        return "BetterReads/0.1 (book-tracking-app; " + properties.contactEmail() + ")";
    }
}
