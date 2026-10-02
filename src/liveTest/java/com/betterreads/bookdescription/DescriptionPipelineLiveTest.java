package com.betterreads.bookdescription;

import static com.betterreads.testsupport.Books.DUNE_AUTHOR;
import static com.betterreads.testsupport.Books.DUNE_ISBN;
import static com.betterreads.testsupport.Books.DUNE_QID;
import static com.betterreads.testsupport.Books.DUNE_TITLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.clients.itunes.ItunesApi;
import com.betterreads.clients.itunes.ItunesDescriptionSource;
import com.betterreads.clients.itunes.ItunesProperties;
import com.betterreads.clients.itunes.ItunesTestWebClientConfig;
import com.betterreads.clients.wikidata.WikidataApi;
import com.betterreads.clients.wikidata.WikidataProperties;
import com.betterreads.clients.wikidata.WikidataWebClientConfig;
import com.betterreads.clients.wikipedia.WikipediaApi;
import com.betterreads.clients.wikipedia.WikipediaDescriptionSource;
import com.betterreads.clients.wikipedia.WikipediaProperties;
import com.betterreads.clients.wikipedia.WikipediaWebClientConfig;
import com.betterreads.ratelimit.RateLimiter;
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

/** Checks description selection against live Wikidata, Wikipedia, and Apple Books data. */
@SpringBootTest(
    classes = {
        WikidataWebClientConfig.class,
        WikipediaWebClientConfig.class,
        DescriptionPipelineLiveTest.ItunesBeans.class,
        ItunesTestWebClientConfig.class,
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

    private static final int MEANINGFUL_LENGTH = 100;

    @Autowired
    private WikipediaDescriptionSource wikipedia;

    @Autowired
    private ItunesDescriptionSource itunes;

    @Test
    @DisplayName("Wikipedia returns usable prose for Dune")
    void wikipediaReturnsUsableDuneDescription() {
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
    @DisplayName("Apple Books returns a Dune blurb by ISBN")
    void appleBooksReturnsDuneDescriptionByIsbn() {
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
    @DisplayName("the selector replaces a weak seed with a longer live description")
    void selectorReplacesWeakSeedWithLongerDescription() {
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
        RateLimiter itunesRateLimiter() {
            return maxWait -> true;
        }
    }
}
