package com.betterreads.integration.loc;

import com.betterreads.common.web.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds the Library of Congress {@link WebClient} with explicit connect and response timeouts.
 *
 * <p>The {@code User-Agent} identifies the application because the SRU endpoint rejects requests
 * with no agent.
 */
@Configuration
public class LocWebClientConfig {

    private static final String USER_AGENT_VALUE = "BetterReads/0.1 (book-tracking-app)";

    private final LocProperties properties;

    public LocWebClientConfig(final LocProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient locWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
            .build();
    }
}
