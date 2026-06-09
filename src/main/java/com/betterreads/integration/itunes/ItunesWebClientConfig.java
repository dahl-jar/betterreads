package com.betterreads.integration.itunes;

import com.betterreads.common.ratelimit.DistributedRateLimiter;
import com.betterreads.common.ratelimit.RateLimiter;
import com.betterreads.common.web.WebClients;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds the Apple Books {@link WebClient} and its shared, Redis-backed rate limiter.
 */
@Configuration
public class ItunesWebClientConfig {

    private static final String USER_AGENT_VALUE =
        "BetterReads/0.1 (https://betterreadsapp.com; book-tracking-app)";

    private static final String RATE_LIMIT_KEY = "itunes:search";

    private final ItunesProperties properties;

    public ItunesWebClientConfig(final ItunesProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient itunesWebClient() {
        return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout())
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
            .build();
    }

    @Bean
    RateLimiter itunesRateLimiter(final ProxyManager<String> rateLimitProxyManager) {
        return new DistributedRateLimiter(
            rateLimitProxyManager, RATE_LIMIT_KEY, properties.ratePerMinute());
    }
}
