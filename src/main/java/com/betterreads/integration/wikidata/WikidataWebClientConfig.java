package com.betterreads.integration.wikidata;

import com.betterreads.common.web.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds the Wikidata {@link WebClient} with explicit connect and response timeouts.
 *
 * <p>The {@code User-Agent} identifies the application and a contact URL, which the Wikimedia APIs
 * require. A single {@code EntityData} document runs to a few hundred kilobytes, past the 256 KB
 * default decode buffer, so the limit is raised to 4 MB.
 */
@Configuration
public class WikidataWebClientConfig {

    private static final String USER_AGENT_VALUE =
        "BetterReads/0.1 (https://betterreadsapp.com; book-tracking-app)";

    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private final WikidataProperties properties;

    public WikidataWebClientConfig(final WikidataProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient wikidataWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
            .build();
    }
}
