package com.betterreads.clients.coverfetch;

import com.betterreads.clients.http.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Cover download client.
 *
 * <p>Timeouts keep a slow cover host from stalling a mirror. Covers outgrow the default codec
 * buffer, so it is sized by {@code cover-fetch.max-bytes}.
 */
@Configuration
class CoverFetchWebClientConfig {

    private final CoverFetchProperties properties;

    CoverFetchWebClientConfig(final CoverFetchProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient coverFetchWebClient() {
        return WebClients.builderWithTimeouts(
                "", properties.connectTimeout(), properties.readTimeout())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(properties.maxBytes()))
            .defaultHeader(HttpHeaders.USER_AGENT, WebClients.USER_AGENT)
            .build();
    }
}
