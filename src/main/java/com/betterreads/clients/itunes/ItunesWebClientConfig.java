package com.betterreads.clients.itunes;

import com.betterreads.clients.http.WebClients;
import com.betterreads.ratelimit.DistributedRateLimiter;
import com.betterreads.ratelimit.RateLimiter;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/** Apple Books client and its Redis-backed rate limiter. */
@Configuration
class ItunesWebClientConfig {

    private static final String RATE_LIMIT_KEY = "itunes:search";

    private final ItunesProperties properties;

    ItunesWebClientConfig(final ItunesProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient itunesWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, WebClients.USER_AGENT)
            .build();
    }

    @Bean
    RateLimiter itunesRateLimiter(final ProxyManager<String> rateLimitProxyManager) {
        return new DistributedRateLimiter(
            rateLimitProxyManager, RATE_LIMIT_KEY, properties.ratePerMinute());
    }
}
