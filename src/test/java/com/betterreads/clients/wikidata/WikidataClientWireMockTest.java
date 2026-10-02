package com.betterreads.clients.wikidata;

import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import static com.betterreads.clients.wikidata.WikidataEntityJson.FIXTURE_QID;
import static com.betterreads.clients.wikidata.WikidataEntityJson.HERBERT_IMAGE;
import static com.betterreads.clients.wikidata.WikidataEntityJson.HERBERT_NAME;
import static com.betterreads.clients.wikidata.WikidataEntityJson.HERBERT_QID;
import static com.betterreads.clients.wikidata.WikidataEntityJson.HERBERT_WIKI_URL;
import static com.betterreads.clients.wikidata.WikidataEntityJson.MOORE_NAME;
import static com.betterreads.clients.wikidata.WikidataEntityJson.MOORE_QID;
import static com.betterreads.clients.wikidata.WikidataEntityJson.entity;
import static com.betterreads.clients.wikidata.WikidataEntityJson.herbert;
import static com.betterreads.clients.wikidata.WikidataEntityJson.moore;
import static com.betterreads.clients.wikidata.WikidataSearchJson.search;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * The search stub returns the real "Dune" ranking, where the film outranks the novel, so the
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

    private static final String FILM_QID = "Q60834962";
    private static final String DUNE_TITLE = "Dune";

    private static final String DRIFT_QID = "Q1138110";

    private static final String MISSING_AUTHOR_QID = "Q404";

    private static final String REDIRECTED_AUTHOR_QID = "Q301";

    private static final String UNLABELLED_QID = "Q999";

    @Autowired
    private WikidataClientImpl client;

    private static WikidataEntityJson novel() {
        return entity().withoutGenres().withoutAwards().withoutSeries().withoutLccn();
    }

    private static WikidataEntityJson film() {
        return entity().withId(FILM_QID).withLabel(DUNE_TITLE).withoutClaims().withInstanceOf("Q11424");
    }

    private static void stubDuneResolution() {
        stubSearch(search());
        stubEntity(film());
        stubEntity(novel());
        stubEntity(herbert());
    }

    private static void stubOnlyHit(final WikidataEntityJson work) {
        stubSearch(search().withOnlyHit(FIXTURE_QID, DUNE_TITLE));
        stubEntity(work);
    }

    @Nested
    class FetchByTitleAuthor {

        @Test
        void picksTheNovelOverTheFilmThatOutranksIt() {
            stubDuneResolution();

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME);

            assertThat(result).isPresent().get().satisfies(book -> {
                assertThat(book.wikidataQid()).isEqualTo(FIXTURE_QID);
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
                        .isEqualTo("https://commons.wikimedia.org/wiki/Special:FilePath/" + HERBERT_IMAGE);
                    assertThat(author.bio()).isEqualTo(HERBERT_WIKI_URL);
                });
        }

        @Test
        void shouldLeaveThePhotoNullWhenTheAuthorHasNoImage() {
            stubOnlyHit(novel().withAuthors(MOORE_QID));
            stubEntity(moore());

            final SourceBook book = client.fetchByTitleAuthor(DUNE_TITLE, MOORE_NAME).orElseThrow();

            assertThat(book.authors()).singleElement().extracting(SourceAuthor::photoUrl).isNull();
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
            stubEntity(herbert());

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldSkipACandidateWithoutAnEnglishName() {
            stubSearch(search().withOnlyHit(UNLABELLED_QID, DUNE_TITLE));
            stubEntity(novel().withId(UNLABELLED_QID).withoutLabels().withoutSitelinks());
            stubEntity(herbert());

            final Optional<SourceBook> result = client.fetchByTitleAuthor(UNLABELLED_QID, HERBERT_NAME);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldSkipAnAuthorWhoseEntityIsNotFound() {
            stubEntityStatus(MISSING_AUTHOR_QID, HTTP_NOT_FOUND);

            assertAuthorSkipped(MISSING_AUTHOR_QID);
        }

        @Test
        void shouldSkipAnAuthorWhoseQidRedirects() {
            stubRedirect(REDIRECTED_AUTHOR_QID, moore());

            assertAuthorSkipped(REDIRECTED_AUTHOR_QID);
        }

        private void assertAuthorSkipped(final String skippedQid) {
            stubOnlyHit(novel().withAuthors(skippedQid, HERBERT_QID));
            stubEntity(herbert());

            final SourceBook book = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME).orElseThrow();

            assertThat(book.authors()).extracting(SourceAuthor::name).containsExactly(HERBERT_NAME);
        }

        @Test
        void shouldKeepEveryAuthorInOrder() {
            stubOnlyHit(novel().withAuthors(HERBERT_QID, MOORE_QID));
            stubEntity(herbert());
            stubEntity(moore());

            final SourceBook book = client.fetchByTitleAuthor(DUNE_TITLE, MOORE_NAME).orElseThrow();

            assertThat(book.authors()).extracting(SourceAuthor::name).containsExactly(HERBERT_NAME, MOORE_NAME);
        }
    }

    @Nested
    class Failures {

        @Test
        void returnsEmptyWhenTheEntityIs404() {
            stubSearch(search().withOnlyHit(FIXTURE_QID, DUNE_TITLE));
            stubEntityStatus(FIXTURE_QID, HTTP_NOT_FOUND);

            final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME);

            assertThat(result).isEmpty();
        }

        @Test
        void propagatesWhenTheEntityIs5xx() {
            stubSearch(search().withOnlyHit(FIXTURE_QID, DUNE_TITLE));
            stubEntityStatus(FIXTURE_QID, HTTP_SERVER_ERROR);

            Assertions.assertThatThrownBy(() -> client.fetchByTitleAuthor(DUNE_TITLE, HERBERT_NAME))
                .isInstanceOf(WebClientResponseException.class);
        }
    }
}
