package com.betterreads.integration.wikidata.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps a Wikidata entity onto a {@link SourceBook}.
 *
 * <p>Each entity is an inline {@code Special:EntityData} document holding only the claims under
 * test. QID labels resolve from a fixed table, so the map runs without HTTP.
 */
class WikidataMapperTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static final String HERBERT = "Frank Herbert";
    private static final String MOORE = "Alan Moore";
    private static final String GIBBONS = "Dave Gibbons";
    private static final String DUNE = "Dune";
    private static final String ICE_AND_FIRE = "A Song of Ice and Fire";
    private static final String SCI_FI = "science fiction";
    private static final String NEBULA = "Nebula Award for Best Novel";
    private static final String HUGO = "Hugo Award for Best Novel";
    private static final String SEIUN = "Seiun Award for Best Translated Long Work";
    private static final String DUNE_QID = "Q190192";
    private static final int DUNE_YEAR = 1965;

    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("Q7934", HERBERT),
        Map.entry("Q205739", MOORE),
        Map.entry("Q445765", GIBBONS),
        Map.entry("Q6095696", DUNE),
        Map.entry("Q45875", ICE_AND_FIRE),
        Map.entry("Q905770", "soft science fiction"),
        Map.entry("Q2630193", "planetary romance"),
        Map.entry("Q944250", "social science fiction"),
        Map.entry("Q24925", SCI_FI),
        Map.entry("Q21802675", "adventure fiction"),
        Map.entry("Q266012", NEBULA),
        Map.entry("Q255032", HUGO),
        Map.entry("Q27496509", SEIUN));

    private static SourceBook map(final String qid, final String entityJson) {
        return new WikidataMapper().toSourceBook(JSON.readTree(entityJson), qid, LABELS::get)
            .orElseThrow();
    }

    @Nested
    class Dune {

        private final SourceBook book = map(DUNE_QID, """
            {"id": "Q190192", "labels": {"en": {"value": "Dune"}}, "claims": {
              "P31":  [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q7725634"}}}}],
              "P50":  [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q7934"}}}}],
              "P136": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q905770"}}}},
                       {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q2630193"}}}},
                       {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q944250"}}}},
                       {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q24925"}}}},
                       {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q21802675"}}}}],
              "P166": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q266012"}}}},
                       {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q255032"}}}},
                       {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q27496509"}}}}],
              "P179": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q6095696"}}},
                        "qualifiers": {"P1545": [{"datavalue": {"value": "1"}}]}}],
              "P244": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": "no2006084758"}}}],
              "P577": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+1965-00-00T00:00:00Z"}}}}],
              "P648": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": "OL893527W"}}}]
            }}""");

        @Test
        void resolvesTheAuthorViaTheEnwikiFallback() {
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
            assertThat(book.wikidataQid()).isEqualTo(DUNE_QID);
            assertThat(book.openLibraryWorkKey()).isEqualTo("OL893527W");
            assertThat(book.locLccn()).isEqualTo("no2006084758");
            assertThat(book.publicationYear()).isEqualTo(DUNE_YEAR);
        }
    }

    @Nested
    class Sandman {

        private final SourceBook book = map("Q827099", """
            {"id": "Q827099", "labels": {"en": {"value": "The Sandman"}}, "claims": {
              "P31": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q14406742"}}}}],
              "P50": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q205739"}}}}]
            }}""");

        @Test
        void leavesGenreNullWhenP136IsAbsent() {
            assertThat(book.rawSubjects()).isNull();
        }

        @Test
        void leavesSeriesNullWhenP179IsAbsent() {
            assertThat(book.seriesName()).isNull();
            assertThat(book.seriesPosition()).isNull();
        }

        @Test
        void leavesOpenLibraryKeyNullWhenP648IsAbsent() {
            assertThat(book.openLibraryWorkKey()).isNull();
        }
    }

    @Nested
    class Watchmen {

        private final SourceBook book = map("Q128444", """
            {"id": "Q128444", "labels": {"en": {"value": "Watchmen"}}, "claims": {
              "P31": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q3297186"}}}},
                      {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q7725634"}}}}],
              "P50": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q205739"}}}},
                      {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q445765"}}}}]
            }}""");

        @Test
        void mapsEveryAuthorWhenP50IsMultiValued() {
            assertThat(book.authors()).extracting(SourceAuthor::name)
                .containsExactly(MOORE, GIBBONS);
        }

        @Test
        void mapsAsAWorkWhenInstanceOfCarriesAWrittenWorkType() {
            assertThat(book.title()).isEqualTo("Watchmen");
        }
    }

    @Nested
    class Hobbit {

        private final SourceBook book = map("Q74287", """
            {"id": "Q74287", "labels": {"en": {"value": "The Hobbit"}}, "claims": {
              "P31":  [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q7725634"}}}}],
              "P244": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": "n79102640"}}}]
            }}""");

        @Test
        void leavesSeriesNullForAStandaloneWork() {
            assertThat(book.seriesName()).isNull();
        }

        @Test
        void readsTheLccnWhenPresent() {
            assertThat(book.locLccn()).isEqualTo("n79102640");
        }

        @Test
        void returnsEmptyAwardsSoStaleRowsClear() {
            assertThat(book.awards()).isEmpty();
        }
    }

    @Nested
    class Clash {

        private final SourceBook book = map("Q300370", """
            {"id": "Q300370", "labels": {"en": {"value": "A Clash of Kings"}}, "claims": {
              "P31":  [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q7725634"}}}}],
              "P179": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q45875"}}},
                        "qualifiers": {"P1545": [{"datavalue": {"value": "2"}}]}}],
              "P648": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": "OL1955946W"}}},
                       {"mainsnak": {"snaktype": "value", "datavalue": {"value": "OL257939W"}}}]
            }}""");

        @Test
        void takesTheFirstOpenLibraryKeyWhenP648IsMultiValued() {
            assertThat(book.openLibraryWorkKey()).isEqualTo("OL1955946W");
        }

        @Test
        void readsTheSeriesPosition() {
            assertThat(book.seriesName()).isEqualTo(ICE_AND_FIRE);
            assertThat(book.seriesPosition()).isEqualTo(2);
        }
    }
}
