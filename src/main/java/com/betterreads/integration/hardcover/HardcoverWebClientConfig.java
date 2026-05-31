package com.betterreads.integration.hardcover;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the Hardcover {@link WebClient} with explicit connect and response timeouts.
 *
 * <p>The token is sent as {@code Authorization: Bearer <token>} only when one is configured, so the
 * context starts without a token and the client returns empty rather than sending a malformed header.
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
        final HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeout())
            .responseTimeout(Duration.ofMillis(properties.readTimeout()));

        final WebClient.Builder builder = WebClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .clientConnector(new ReactorClientHttpConnector(httpClient));

        final String token = properties.bearerToken();
        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token);
        }
        return builder.build();
    }
}
