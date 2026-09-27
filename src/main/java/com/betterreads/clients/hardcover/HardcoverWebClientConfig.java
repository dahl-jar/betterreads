package com.betterreads.clients.hardcover;

import com.betterreads.clients.http.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Hardcover {@code WebClient} with connect and read timeouts.
 *
 * <p>A secret sealed with a trailing newline gives a header value the HTTP client rejects, so the token
 * is stripped. Without a token no Authorization header is sent and the Hardcover clients return empty.
 */
@Configuration
public class HardcoverWebClientConfig {

    private static final String BEARER_PREFIX = "Bearer ";

    private final HardcoverProperties properties;

    public HardcoverWebClientConfig(final HardcoverProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient hardcoverWebClient() {
        final WebClient.Builder builder = WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, WebClients.USER_AGENT)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        final String token = properties.bearerToken();
        if (!token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token.strip());
        }
        return builder.build();
    }
}
