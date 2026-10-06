package com.betterreads.clients.websearch;

import com.betterreads.clients.http.PublicUrlGuard;
import com.betterreads.clients.http.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class SourcePageWebClientConfig {

    private final WebSearchProperties properties;

    private final PublicUrlGuard guard;

    SourcePageWebClientConfig(final WebSearchProperties properties, final PublicUrlGuard guard) {
        this.properties = properties;
        this.guard = guard;
    }

    @Bean
    WebClient sourcePageWebClient() {
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(WebClients.publicHttpClient(
                properties.pageConnectTimeout(), properties.pageReadTimeout(), guard).compress(true)))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(properties.pageMaxBytes()))
            .defaultHeader(HttpHeaders.USER_AGENT, WebClients.USER_AGENT)
            .build();
    }
}
