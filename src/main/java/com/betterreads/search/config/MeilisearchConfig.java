package com.betterreads.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the singleton Meilisearch {@link Client} from {@link MeilisearchProperties}.
 */
@Configuration
public class MeilisearchConfig {

    /**
     * Returns a Meilisearch client wired with the configured host and master key.
     */
    @Bean
    public Client meilisearchClient(final MeilisearchProperties props) {
        return new Client(new Config(props.host(), props.masterKey()));
    }
}
