package com.betterreads.integration.wikipedia;

import com.betterreads.common.web.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds the Wikipedia {@link WebClient} with explicit connect and response timeouts.
 *
 * <p>The {@code User-Agent} identifies the application and a contact URL, which the Wikimedia APIs
 * require.
 */
@Configuration
public class WikipediaWebClientConfig {

    private static final String USER_AGENT_VALUE =
        "BetterReads/0.1 (https://betterreadsapp.com; book-tracking-app)";

    private final WikipediaProperties properties;

    public WikipediaWebClientConfig(final WikipediaProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient wikipediaWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
            .build();
    }
}
