package com.betterreads.clients.loc;

import com.betterreads.clients.http.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Library of Congress web client. The SRU endpoint rejects requests without a User-Agent, so one is
 * always sent.
 */
@Configuration
class LocWebClientConfig {

    private final LocProperties properties;

    LocWebClientConfig(final LocProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient locWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, WebClients.USER_AGENT)
            .build();
    }
}
