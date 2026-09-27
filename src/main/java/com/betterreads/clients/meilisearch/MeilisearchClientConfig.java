package com.betterreads.clients.meilisearch;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MeilisearchClientConfig {

    @Bean
    public Client meilisearchClient(final MeilisearchProperties props) {
        return new Client(new Config(props.host(), props.masterKey()));
    }
}
