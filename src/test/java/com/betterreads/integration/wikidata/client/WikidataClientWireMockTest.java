package com.betterreads.integration.wikidata.client;

import static com.betterreads.integration.wikidata.WikidataEntityJson.entity;
import static com.betterreads.integration.wikidata.WikidataSearchJson.search;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.wikidata.WikidataApi;
import com.betterreads.integration.wikidata.WikidataEntityJson;
import com.betterreads.integration.wikidata.WikidataProperties;
import com.betterreads.integration.wikidata.WikidataWebClientConfig;
import com.betterreads.integration.wikidata.WikidataWireMock;
import com.betterreads.integration.wikidata.mapper.WikidataMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Exercises the Wikidata client request path against a stubbed boundary: candidate disambiguation,
 * author resolution, and the 4xx-to-empty / 5xx-propagates contract.
 *
 * <p>The search stub returns the real "Dune" ranking, where the film outranks the novel, so the
 * resolver must skip the top hit and pick the literary work.
 */
@SpringBootTest(
    classes = {
        WikidataWebClientConfig.class,
        WikidataApi.class,
        WikidataClientImpl.class,
        WikidataMapper.class
    },
    properties = "spring.main.web-application-type=none"
)
@EnableConfigurationProperties(WikidataProperties.class)
class WikidataClientWireMockTest extends WikidataWireMock {

    private static final String HERBERT_WIKI_URL = "https://en.wikipedia.org/wiki/Frank_Herbert";

    private static final String FILM_QID = "Q60834962";
    private static final String NOVEL_QID = "Q190192";
    private static final String AUTHOR_QID = "Q7934";
    private static final String DUNE_TITLE = "Dune";
    private static final String HERBERT_NAME = "Frank Herbert";

    private static final String DRIFT_QID = "Q1138110";

    @Autowired
    private WikidataClientImpl client;

    private static WikidataEntityJson novel() {
        return entity().withoutGenres().withoutAwards().withoutSeries().withoutLccn();
    }

    private static WikidataEntityJson film() {
        return entity().withId(FILM_QID).withLabel(DUNE_TITLE).withoutClaims().withInstanceOf("Q11424");
    }

    private static WikidataEntityJson author() {
        return entity().withId(AUTHOR_QID).withoutLabels().withSitelink(HERBERT_NAME, HERBERT_WIKI_URL)
            .withoutClaims().withImage("Frank Herbert 1984.jpg");
    }

    private static void stubDuneResolution() {
        stubSearch(search());
        stubEntity(film());
        stubEntity(novel());
        stubEntity(author());
    }

    @Nested
    class FetchByTitleAuthor {

        @Test
        void picksTheNovelOverTheFilmThatOutranksIt() {
            stubDuneResolution();

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME);

            assertThat(result).isPresent().get().satisfies(book -> {
                assertThat(book.wikidataQid()).isEqualTo(NOVEL_QID);
                assertThat(book.openLibraryWorkKey()).isEqualTo("OL893527W");
            });
        }

        @Test
        void readsTheAuthorPhotoAndBioFromTheAuthorEntity() {
            stubDuneResolution();

            final SourceBook book = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME).orElseThrow();

            assertThat(book.authors())
                .singleElement()
                .satisfies(author -> {
                    assertThat(author.name()).isEqualTo(HERBERT_NAME);
                    assertThat(author.photoUrl())
                        .isEqualTo("https://commons.wikimedia.org/wiki/Special:FilePath/Frank Herbert 1984.jpg");
                    assertThat(author.bio()).isEqualTo(HERBERT_WIKI_URL);
                });
        }

        @Test
        void returnsEmptyWhenNoCandidateMatchesTheAuthor() {
            stubDuneResolution();

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, "Ursula K. Le Guin");

            assertThat(result).isEmpty();
        }

        @Test
        void rejectsASameAuthorWorkWhoseTitleDriftsFromTheQuery() {
            final String santaroga = "The Santaroga Barrier";
            stubSearch(search().withOnlyHit(DRIFT_QID, santaroga));
            stubEntity(novel().withId(DRIFT_QID).withLabel(santaroga));
            stubEntity(author());

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class FetchByQid {

        @Test
        void mapsTheEntityDirectlyWithoutSearching() {
            stubEntity(novel());
            stubEntity(author());

            final Optional<SourceBook> result = client.fetchByQid(NOVEL_QID);

            assertThat(result).isPresent().get()
                .extracting(SourceBook::title).isEqualTo(DUNE_TITLE);
        }

        @Test
        void returnsEmptyWhenTheQidIsNotAWrittenWork() {
            stubEntity(film());

            assertThat(client.fetchByQid(FILM_QID)).isEmpty();
        }
    }

    @Nested
    class Failures {

        @Test
        void returnsEmptyWhenTheEntityIs404() {
            stubEntityStatus(NOVEL_QID, HTTP_NOT_FOUND);

            assertThat(client.fetchByQid(NOVEL_QID)).isEmpty();
        }

        @Test
        void propagatesWhenTheEntityIs5xx() {
            stubEntityStatus(NOVEL_QID, HTTP_SERVER_ERROR);

            Assertions.assertThatThrownBy(() -> client.fetchByQid(NOVEL_QID))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
