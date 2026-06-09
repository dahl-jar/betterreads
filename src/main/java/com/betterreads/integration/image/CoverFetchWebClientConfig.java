package com.betterreads.integration.image;

import com.betterreads.common.web.WebClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds the {@link WebClient} that downloads external cover images, with explicit timeouts so a slow
 * cover host cannot stall a mirror.
 *
 * <p>The body buffer is sized by {@code cover-fetch.max-bytes}; covers outgrow the shared
 * JSON-sized source buffer.
 */
@Configuration
public class CoverFetchWebClientConfig {

    private static final String USER_AGENT_VALUE =
        "BetterReads/0.1 (https://betterreadsapp.com; book-tracking-app)";

    private final CoverFetchProperties properties;

    public CoverFetchWebClientConfig(final CoverFetchProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient coverFetchWebClient() {
        return WebClients.builderWithTimeouts(
                "", properties.connectTimeout(), properties.readTimeout())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(properties.maxBytes()))
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
            .build();
    }
}
