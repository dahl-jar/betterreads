package com.betterreads.clients.coverfetch;

import com.betterreads.clients.http.PublicUrlGuard;
import com.betterreads.clients.http.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
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

    private final PublicUrlGuard guard;

    CoverFetchWebClientConfig(final CoverFetchProperties properties, final PublicUrlGuard guard) {
        this.properties = properties;
        this.guard = guard;
    }

    @Bean
    WebClient coverFetchWebClient() {
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                WebClients.publicHttpClient(properties.connectTimeout(), properties.readTimeout(), guard)))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(properties.maxBytes()))
            .defaultHeader(HttpHeaders.USER_AGENT, WebClients.USER_AGENT)
            .build();
    }
}
