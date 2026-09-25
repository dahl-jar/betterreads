package com.betterreads.integration.wikidata.mapper;

import static com.betterreads.integration.wikidata.WikidataEntityJson.FIXTURE_QID;
import static com.betterreads.integration.wikidata.WikidataEntityJson.entity;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import com.betterreads.catalog.service.source.model.SourceAuthor;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.wikidata.WikidataEntityJson;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WikidataMapperTest {

    private static final String HERBERT = "Frank Herbert";
    private static final String MOORE = "Alan Moore";
    private static final String GIBBONS = "Dave Gibbons";
    private static final String DUNE = "Dune";
    private static final String SCI_FI = "science fiction";
    private static final String NEBULA = "Nebula Award for Best Novel";
    private static final String HUGO = "Hugo Award for Best Novel";
    private static final String SEIUN = "Seiun Award for Best Translated Long Work";
    private static final int DUNE_YEAR = 1965;

    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("Q7934", HERBERT),
        Map.entry("Q205739", MOORE),
        Map.entry("Q445765", GIBBONS),
        Map.entry("Q6095696", DUNE),
        Map.entry("Q905770", "soft science fiction"),
        Map.entry("Q2630193", "planetary romance"),
        Map.entry("Q944250", "social science fiction"),
        Map.entry("Q24925", SCI_FI),
        Map.entry("Q21802675", "adventure fiction"),
        Map.entry("Q266012", NEBULA),
        Map.entry("Q255032", HUGO),
        Map.entry("Q27496509", SEIUN));

    private static SourceBook map(final WikidataEntityJson entity) {
        return new WikidataMapper().toSourceBook(entity.node(), FIXTURE_QID, LABELS::get).orElseThrow();
    }

    private static String qidOf(final String label) {
        return LABELS.entrySet().stream()
            .filter(entry -> entry.getValue().equals(label))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    }

    @Nested
    class FullEntity {

        private final SourceBook book = map(entity());

        @Test
        void resolvesTheAuthorNameFromTheP50Qid() {
            assertThat(book.authors()).extracting(SourceAuthor::name).containsExactly(HERBERT);
        }

        @Test
        void reducesGenresToCanonicalTerms() {
            assertThat(book.rawSubjects())
                .containsExactlyInAnyOrder(SCI_FI, "romance", "fiction");
        }

        @Test
        void readsSeriesNameAndPositionFromTheP179Qualifier() {
            assertThat(book.seriesName()).isEqualTo(DUNE);
            assertThat(book.seriesPosition()).isEqualTo(1);
        }

        @Test
        void readsAllAwards() {
            assertThat(book.awards()).containsExactlyInAnyOrder(
                NEBULA, HUGO, SEIUN);
        }

        @Test
        void readsTheIdentifiersAndYear() {
            assertThat(book.wikidataQid()).isEqualTo(FIXTURE_QID);
            assertThat(book.openLibraryWorkKey()).isEqualTo("OL893527W");
            assertThat(book.locLccn()).isEqualTo("no2006084758");
            assertThat(book.publicationYear()).isEqualTo(DUNE_YEAR);
        }
    }

    @Nested
    class WithoutOptionalClaims {

        private final SourceBook book = map(entity().withoutGenres().withoutSeries());

        @Test
        void leavesGenreNullWhenP136IsAbsent() {
            assertThat(book.rawSubjects()).isNull();
        }

        @Test
        void leavesSeriesNullWhenP179IsAbsent() {
            assertThat(book.seriesName()).isNull();
            assertThat(book.seriesPosition()).isNull();
        }
    }

    @Nested
    class WithSeveralAuthors {

        private final SourceBook book = map(entity().withAuthors(qidOf(MOORE), qidOf(GIBBONS)));

        @Test
        void mapsEveryAuthorWhenP50IsMultiValued() {
            assertThat(book.authors()).extracting(SourceAuthor::name)
                .containsExactly(MOORE, GIBBONS);
        }
    }

    @Nested
    class WithoutAwards {

        private final SourceBook book = map(entity().withoutAwards());

        @Test
        void returnsEmptyAwardsSoStaleRowsClear() {
            assertThat(book.awards()).isEmpty();
        }
    }

    @Nested
    class WithSeveralOpenLibraryKeys {

        private static final String FIRST_KEY = "OL1955946W";

        private final SourceBook book = map(entity().withOpenLibraryKeys(FIRST_KEY, "OL257939W"));

        @Test
        void takesTheFirstOpenLibraryKeyWhenP648IsMultiValued() {
            assertThat(book.openLibraryWorkKey()).isEqualTo(FIRST_KEY);
        }
    }

    @Nested
    class WithoutId {

        @Test
        void returnsEmptyWhenTheEntityCarriesNoId() {
            final WikidataEntityJson noId = entity().withoutId();

            final Optional<SourceBook> mapped =
                new WikidataMapper().toSourceBook(noId.node(), FIXTURE_QID, LABELS::get);

            assertThat(mapped)
                .as("should skip an entity with no id because it has no display name")
                .isEmpty();
        }
    }
}
