package com.betterreads.integration.wikidata.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.betterreads.catalog.service.SourceAuthor;
import com.betterreads.catalog.service.SourceBook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps real {@code Special:EntityData} fixtures onto a {@link SourceBook}.
 *
 * <p>QID labels are resolved from a fixed table of values captured from the live API, so the map
 * runs without HTTP.
 */
class WikidataMapperTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static final String DUNE_QID = "Q190192";
    private static final String HERBERT = "Frank Herbert";
    private static final String MOORE = "Alan Moore";
    private static final String GIBBONS = "Dave Gibbons";
    private static final String DUNE_SERIES = "Dune";
    private static final String ICE_AND_FIRE = "A Song of Ice and Fire";
    private static final String SCI_FI = "science fiction";
    private static final String NEBULA = "Nebula Award for Best Novel";
    private static final String HUGO = "Hugo Award for Best Novel";
    private static final String SEIUN = "Seiun Award for Best Translated Long Work";

    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("Q111984153", "young adult fiction"),
        Map.entry("Q132311", "fantasy"),
        Map.entry("Q166351", "Robert Jordan"),
        Map.entry("Q16657177", "alternate history comics"),
        Map.entry("Q181677", "George R. R. Martin"),
        Map.entry("Q205739", MOORE),
        Map.entry("Q20899118", "NPR Top 100 Science Fiction and Fantasy Books"),
        Map.entry("Q210059", "Neil Gaiman"),
        Map.entry("Q21802675", "adventure fiction"),
        Map.entry("Q24925", SCI_FI),
        Map.entry("Q25448739", "Eisner Award for Best Limited Series"),
        Map.entry("Q255032", HUGO),
        Map.entry("Q2630193", "planetary romance"),
        Map.entry("Q266012", NEBULA),
        Map.entry("Q27496509", SEIUN),
        Map.entry("Q326439", "high fantasy"),
        Map.entry("Q3811649", "juvenile fantasy"),
        Map.entry("Q445765", GIBBONS),
        Map.entry("Q4470", "The Wheel of Time"),
        Map.entry("Q45875", ICE_AND_FIRE),
        Map.entry("Q607354", "Locus Award for Best Fantasy Novel"),
        Map.entry("Q6095696", DUNE_SERIES),
        Map.entry("Q6359432", "Ignotus Award for Best Foreign Novel"),
        Map.entry("Q699", "fairy tale"),
        Map.entry("Q7643429", "superhero comics"),
        Map.entry("Q7934", HERBERT),
        Map.entry("Q892", "J. R. R. Tolkien"),
        Map.entry("Q905770", "soft science fiction"),
        Map.entry("Q944250", "social science fiction"));

    private static final int DUNE_YEAR = 1965;

    private final WikidataMapper mapper = new WikidataMapper();

    private SourceBook map(final String qid) {
        return mapper.toSourceBook(entity(qid), qid, LABELS::get).orElseThrow();
    }

    @Nested
    class Dune {

        private final SourceBook book = map(DUNE_QID);

        @Test
        void resolvesTheAuthorViaTheEnwikiFallback() {
            assertThat(book.authors())
                .extracting(SourceAuthor::name)
                .containsExactly(HERBERT);
        }

        @Test
        void reducesGenresToCanonicalTerms() {
            assertThat(book.rawSubjects()).containsExactlyInAnyOrder(SCI_FI, "romance", "fiction");
        }

        @Test
        void readsSeriesNameAndPositionFromTheP179Qualifier() {
            assertThat(book.seriesName()).isEqualTo(DUNE_SERIES);
            assertThat(book.seriesPosition()).isEqualTo(1);
        }

        @Test
        void readsAllAwards() {
            assertThat(book.awards()).containsExactlyInAnyOrder(NEBULA, HUGO, SEIUN);
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

        private final SourceBook book = map("Q827099");

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

        private final SourceBook book = map("Q128444");

        @Test
        void mapsEveryAuthorWhenP50IsMultiValued() {
            assertThat(book.authors())
                .extracting(SourceAuthor::name)
                .containsExactly(MOORE, GIBBONS);
        }

        @Test
        void mapsAsAWorkWhenInstanceOfCarriesAWrittenWorkType() {
            assertThat(book.title()).isEqualTo("Watchmen");
        }
    }

    @Nested
    class Hobbit {

        private final SourceBook book = map("Q74287");

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

        private final SourceBook book = map("Q300370");

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

    private static JsonNode entity(final String qid) {
        final String json = fixture("entity-" + qid + ".json");
        return JSON.readTree(json).path("entities").path(qid);
    }

    private static String fixture(final String name) {
        try {
            return new ClassPathResource("integration/wikidata/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
