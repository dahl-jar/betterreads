package com.betterreads.integration.hardcover;

import com.betterreads.common.web.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds the Hardcover {@link WebClient} with explicit connect and response timeouts.
 *
 * <p>The token is sent as {@code Authorization: Bearer <token>} only when one is configured, so the
 * context starts without a token and the client returns empty rather than sending a malformed header.
 * The token is stripped of surrounding whitespace, so a secret sealed with a trailing newline still
 * yields a header value the HTTP client accepts.
 */
@Configuration
public class HardcoverWebClientConfig {

    private static final String USER_AGENT_VALUE = "BetterReads/0.1 (book-tracking-app)";

    private static final String BEARER_PREFIX = "Bearer ";

    private final HardcoverProperties properties;

    public HardcoverWebClientConfig(final HardcoverProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient hardcoverWebClient() {
        final WebClient.Builder builder = WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        final String token = properties.bearerToken();
        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token.strip());
        }
        return builder.build();
    }
}
