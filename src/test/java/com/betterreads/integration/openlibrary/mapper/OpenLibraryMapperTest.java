package com.betterreads.integration.openlibrary.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure helpers inside {@link OpenLibraryMapper}.
 *
 * <p>Each case targets a shape OpenLibrary actually returns and a bug a code change could
 * introduce. The fixtures are the real shapes observed on the live API on 2026-05-28: the
 * {@code {type, value}} description object (Lord of the Rings, OL27448W), the machine-tag
 * subjects (Dune, OL893415W), and the {@code cover_i = 0} no-cover case. Hand-invented JSON
 * would miss exactly these.
 */
class OpenLibraryMapperTest {

    private static final String LOTR_DESCRIPTION = "Originally published from 1954 through 1956.";

    private static final String HOBBIT_DESCRIPTION = "A tale of high adventure.";

    private static final String FANTASY = "fantasy";

    private static final String FICTION = "fiction";

    private static final String FANTASY_TITLECASE = "Fantasy";

    private static final String JUVENILE_FANTASY = "juvenile fantasy";

    private static final String THRUSHES = "thrushes";

    private static final String EAGLES = "eagles";

    private static final String GIANT_SPIDERS = "giant spiders";

    private static final String THE_ONE_RING = "the one ring";

    private static final String HOBBIT_WORK_KEY = "OL27482W";

    @Nested
    @DisplayName("coerceDescription (string | {type,value} | null)")
    class CoerceDescription {

        @Test
        @DisplayName("a {type,value} object is flattened to its value text, not Map.toString'd")
        void dictDescriptionYieldsValueText() {
            final Object description = Map.of("type", "/type/text", "value", LOTR_DESCRIPTION);

            assertThat(OpenLibraryMapper.coerceDescription(description))
                .as("a regression that stored Map.toString() would leak '{type=..., value=...}' "
                    + "into the catalog description")
                .isEqualTo(LOTR_DESCRIPTION);
        }

        @Test
        @DisplayName("a plain-string description passes through unchanged")
        void stringDescriptionPassesThrough() {
            assertThat(OpenLibraryMapper.coerceDescription(HOBBIT_DESCRIPTION))
                .isEqualTo(HOBBIT_DESCRIPTION);
        }

        @Test
        @DisplayName("null and blank both collapse to null, never the literal 'null'")
        void absentDescriptionIsNull() {
            assertThat(OpenLibraryMapper.coerceDescription(null)).isNull();
            assertThat(OpenLibraryMapper.coerceDescription("   ")).isNull();
        }
    }

    @Nested
    @DisplayName("cleanSubjects")
    class CleanSubjects {

        @Test
        @DisplayName("the real Hobbit subjects collapse to canonical genres, no verbose variants")
        void hobbitSubjectsReduceToCanonicalGenres() {
            final List<String> hobbitSubjects = List.of(
                FANTASY_TITLECASE, "Arkenstone", "Battle of Five Armies", "invisibility", THRUSHES,
                EAGLES, "hobbits", "wizards", "dragons", GIANT_SPIDERS, THE_ONE_RING,
                JUVENILE_FANTASY, "YOUNG ADULT FICTION", "English Fantasy fiction", "Juvenile fiction",
                "Fiction", "Classics");

            assertThat(OpenLibraryMapper.cleanSubjects(hobbitSubjects))
                .as("verbose OL phrasings (english fantasy fiction, juvenile fantasy) collapse to "
                    + "the canonical genre terms they mention; plot elements drop entirely")
                .containsExactlyInAnyOrder(FANTASY, FICTION, "young adult", "classics")
                .doesNotContain("english fantasy fiction", JUVENILE_FANTASY, THRUSHES, "arkenstone");
        }

        @Test
        @DisplayName("Dune's machine tags drop and verbose science-fiction phrasings collapse to one")
        void duneSubjectsReduceToCanonical() {
            final List<String> duneSubjects = List.of(
                "American science fiction", "Science fiction", "Science-fiction",
                "nyt:mass-market-monthly=2021-11-07", "award:nebula_award=novel",
                "Dune (Imaginary place), fiction");

            assertThat(OpenLibraryMapper.cleanSubjects(duneSubjects))
                .as("the three science-fiction variants collapse to one canonical term; the "
                    + "machine tags and the imaginary-place prefix contribute only their genre word")
                .containsExactlyInAnyOrder("science fiction", FICTION);
        }

        @Test
        @DisplayName("a compound subject contributes only the canonical genres it mentions")
        void compoundSubjectYieldsCanonicalGenres() {
            assertThat(OpenLibraryMapper.cleanSubjects(List.of("action & adventure, american fantasy fiction")))
                .as("the place/marketing prefix is discarded; only the canonical genres remain")
                .containsExactlyInAnyOrder(FANTASY, FICTION)
                .doesNotContain("action & adventure", "american fantasy fiction");
        }

        @Test
        @DisplayName("verbose variants of the same genre collapse to one canonical term")
        void variantsCollapseToOneCanonical() {
            assertThat(OpenLibraryMapper.cleanSubjects(
                    List.of(FANTASY_TITLECASE, "American fantasy fiction", "English fantasy fiction")))
                .as("all three mention fantasy and fiction; the canonical set has each once")
                .containsExactlyInAnyOrder(FANTASY, FICTION);
        }

        @Test
        @DisplayName("canonical genres are capped so a genre-heavy work cannot flood the catalog row")
        void canonicalGenresCapped() {
            final int manyVariants = 93;
            final List<String> subjects = IntStream.range(0, manyVariants)
                .mapToObj(i -> "fantasy variant " + i + " fiction")
                .toList();

            assertThat(OpenLibraryMapper.cleanSubjects(subjects))
                .as("all variants reduce to {fantasy, fiction}; the canonical set is naturally small "
                    + "and never exceeds the cap")
                .hasSizeLessThanOrEqualTo(OpenLibraryMapper.MAX_SUBJECTS)
                .containsExactlyInAnyOrder(FANTASY, FICTION);
        }

        @Test
        @DisplayName("null subjects yield an empty list; the caller decides null-vs-empty semantics")
        void nullSubjectsYieldEmpty() {
            assertThat(OpenLibraryMapper.cleanSubjects(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildCoverUrl")
    class BuildCoverUrl {

        @Test
        @DisplayName("a real cover id builds the canonical -L covers URL the catalog stores")
        void coverIdBuildsUrl() {
            final int hobbitCoverId = 14_627_509;
            assertThat(OpenLibraryMapper.buildCoverUrl(hobbitCoverId))
                .isEqualTo("https://covers.openlibrary.org/b/id/14627509-L.jpg");
        }

        @Test
        @DisplayName("cover_i = 0 is OpenLibrary's no-cover sentinel, mapped to null not '.../0-L.jpg'")
        void coverIdZeroIsNoCover() {
            assertThat(OpenLibraryMapper.buildCoverUrl(0)).isNull();
        }

        @Test
        @DisplayName("a null cover id is null, not a NumberFormat throw")
        void nullCoverIdIsNull() {
            assertThat(OpenLibraryMapper.buildCoverUrl(null)).isNull();
        }
    }

    @Nested
    @DisplayName("stripWorksPrefix")
    class StripWorksPrefix {

        @Test
        @DisplayName("the /works/ prefix is stripped so the bare id is stored")
        void prefixStripped() {
            assertThat(OpenLibraryMapper.stripWorksPrefix("/works/" + HOBBIT_WORK_KEY))
                .isEqualTo(HOBBIT_WORK_KEY);
        }

        @Test
        @DisplayName("an already-bare key is left unchanged, not double-stripped")
        void bareKeyUnchanged() {
            assertThat(OpenLibraryMapper.stripWorksPrefix(HOBBIT_WORK_KEY)).isEqualTo(HOBBIT_WORK_KEY);
        }
    }
}
