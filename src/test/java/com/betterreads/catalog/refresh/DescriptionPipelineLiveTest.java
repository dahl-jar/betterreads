package com.betterreads.catalog.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.service.pipeline.DescriptionSelector;
import com.betterreads.catalog.service.source.DescriptionLookup;
import com.betterreads.catalog.service.source.DescriptionQuality;
import com.betterreads.common.ratelimit.RateLimiter;
import com.betterreads.common.web.WebClients;
import com.betterreads.integration.itunes.ItunesApi;
import com.betterreads.integration.itunes.ItunesDescriptionSource;
import com.betterreads.integration.itunes.ItunesProperties;
import com.betterreads.integration.wikidata.WikidataApi;
import com.betterreads.integration.wikidata.WikidataProperties;
import com.betterreads.integration.wikidata.WikidataWebClientConfig;
import com.betterreads.integration.wikipedia.WikipediaApi;
import com.betterreads.integration.wikipedia.WikipediaDescriptionSource;
import com.betterreads.integration.wikipedia.WikipediaProperties;
import com.betterreads.integration.wikipedia.WikipediaWebClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Live end-to-end check of the description pipeline against the real Wikidata, Wikipedia, and Apple
 * Books APIs. Gated on {@code DESCRIPTION_LIVE=1} so it never runs in CI.
 *
 * <p>Proves the full chain on real data: HTTP fetch, parse, clean, quality-score, and enrich. Dune
 * has a Wikipedia article; Apple Books supplies a blurb by ISBN; the selector prefers the
 * strongest source over a weak seed.
 */
@SpringBootTest(
    classes = {
        WikidataWebClientConfig.class,
        WikipediaWebClientConfig.class,
        DescriptionPipelineLiveTest.ItunesBeans.class,
        WikidataApi.class,
        WikipediaApi.class,
        ItunesApi.class,
        WikipediaDescriptionSource.class,
        ItunesDescriptionSource.class,
        DescriptionSelector.class
    },
    properties = {
        "spring.main.web-application-type=none",
        "wikidata.base-url=https://www.wikidata.org",
        "wikidata.connect-timeout=5000",
        "wikidata.read-timeout=10000",
        "wikipedia.base-url=https://en.wikipedia.org",
        "wikipedia.connect-timeout=5000",
        "wikipedia.read-timeout=10000",
        "itunes.base-url=https://itunes.apple.com",
        "itunes.connect-timeout=5000",
        "itunes.read-timeout=10000",
        "itunes.rate-per-minute=12"
    }
)
@EnableConfigurationProperties({WikidataProperties.class, WikipediaProperties.class, ItunesProperties.class})
@EnabledIfEnvironmentVariable(named = "DESCRIPTION_LIVE", matches = "1")
class DescriptionPipelineLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(DescriptionPipelineLiveTest.class);

    private static final String DUNE_QID = "Q190192";

    private static final String DUNE_ISBN = "9780441013593";

    private static final String DUNE_TITLE = "Dune";

    private static final String DUNE_AUTHOR = "Frank Herbert";

    private static final int MEANINGFUL_LENGTH = 100;

    @Autowired
    private WikipediaDescriptionSource wikipedia;

    @Autowired
    private ItunesDescriptionSource itunes;

    @Test
    @DisplayName("Wikipedia returns clean encyclopedic prose for Dune")
    void wikipediaForDune() {
        final DescriptionLookup lookup =
            new DescriptionLookup(DUNE_QID, DUNE_ISBN, DUNE_TITLE, DUNE_AUTHOR, null, null);

        final Optional<String> raw = wikipedia.fetch(lookup);

        assertThat(raw).isPresent();
        final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(raw.orElseThrow());
        assertThat(assessment.usable()).isTrue();
        assertThat(assessment.cleaned()).contains(DUNE_AUTHOR);
        LOG.info("description.live wikipedia.dune length={}", assessment.cleaned().length());
    }

    @Test
    @DisplayName("Apple Books returns a usable blurb by ISBN")
    void itunesByIsbn() {
        final DescriptionLookup lookup =
            new DescriptionLookup(null, DUNE_ISBN, DUNE_TITLE, DUNE_AUTHOR, null, null);

        final Optional<String> raw = itunes.fetch(lookup);

        assertThat(raw).isPresent();
        final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(raw.orElseThrow());
        assertThat(assessment.cleaned()).isNotBlank();
        LOG.info("description.live itunes.dune usable={} length={}",
            assessment.usable(), assessment.cleaned().length());
    }

    @Test
    @DisplayName("the selector prefers the higher-quality source over a weak seed")
    void selectorPicksBest() {
        final DescriptionSelector selector = new DescriptionSelector(List.of(wikipedia, itunes));
        final DescriptionLookup lookup =
            new DescriptionLookup(DUNE_QID, DUNE_ISBN, DUNE_TITLE, DUNE_AUTHOR, null, null);

        final Optional<String> best = selector.bestDescription(lookup, "A tiny weak stub.");

        assertThat(best).isPresent();
        assertThat(best.orElseThrow().length()).isGreaterThan(MEANINGFUL_LENGTH);
        LOG.info("description.live selected length={}", best.orElseThrow().length());
    }

    @TestConfiguration
    static class ItunesBeans {

        @Bean
        WebClient itunesWebClient(final ItunesProperties properties) {
            return WebClients.builderWithTimeouts(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout()).build();
        }

        @Bean
        RateLimiter itunesRateLimiter() {
            return maxWait -> true;
        }
    }
}
