package com.betterreads.clients.itunes;

import com.betterreads.clients.http.WebClients;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
public class ItunesTestWebClientConfig {

    @Bean
    WebClient itunesWebClient(final ItunesProperties properties) {
        return WebClients.builderWithTimeouts(
            properties.baseUrl(), properties.connectTimeout(), properties.readTimeout()).build();
    }
}
