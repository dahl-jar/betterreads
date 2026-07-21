package com.betterreads.integration.openlibrary.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.openlibrary.dto.SearchDoc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenLibraryMapperTest {

    private static final String LOTR_DESCRIPTION = "Originally published from 1954 through 1956.";

    private static final String HOBBIT_DESCRIPTION = "A tale of high adventure.";

    private static final String HOBBIT_WORK_KEY = "OL27482W";

    private static final int FIXTURE_YEAR = 2014;

    private static final String ENGLISH = "eng";

    private static final String FRENCH = "fre";

    private final OpenLibraryMapper mapper = new OpenLibraryMapper();

    @Nested
    @DisplayName("language")
    class Language {

        @Test
        void prefersEnglishFromMultiLanguageList() {
            final SearchDoc doc = languageDoc(List.of("spa", "tur", ENGLISH, "pol"));

            final SourceBook book = mapper.toSourceBook(doc, null);

            assertThat(book)
                .isNotNull()
                .extracting(SourceBook::language)
                .isEqualTo(ENGLISH);
        }

        @Test
        void keepsFirstWhenNoEnglish() {
            final SearchDoc doc = languageDoc(List.of(FRENCH, "deu"));

            final SourceBook book = mapper.toSourceBook(doc, null);

            assertThat(book)
                .isNotNull()
                .extracting(SourceBook::language)
                .isEqualTo(FRENCH);
        }

        private SearchDoc languageDoc(final List<String> languages) {
            return new SearchDoc(
                "/works/OL1W", "Red Rising", null, List.of("Pierce Brown"), FIXTURE_YEAR, null, null,
                languages);
        }
    }

    @Nested
    @DisplayName("coerceDescription")
    class CoerceDescription {

        @Test
        void objectDescriptionYieldsValueText() {
            final Object description = Map.of("type", "/type/text", "value", LOTR_DESCRIPTION);

            final String coerced = OpenLibraryMapper.coerceDescription(description);

            assertThat(coerced).isEqualTo(LOTR_DESCRIPTION);
        }

        @Test
        void stringDescriptionPassesThrough() {
            final String coerced = OpenLibraryMapper.coerceDescription(HOBBIT_DESCRIPTION);

            assertThat(coerced).isEqualTo(HOBBIT_DESCRIPTION);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = "   ")
        void absentDescriptionIsNull(final Object description) {
            final String coerced = OpenLibraryMapper.coerceDescription(description);

            assertThat(coerced).isNull();
        }
    }

    @Nested
    @DisplayName("buildCoverUrl")
    class BuildCoverUrl {

        @Test
        void coverIdBuildsUrl() {
            final int hobbitCoverId = 14_627_509;

            final String coverUrl = OpenLibraryMapper.buildCoverUrl(hobbitCoverId);

            assertThat(coverUrl).isEqualTo("https://covers.openlibrary.org/b/id/14627509-L.jpg");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(ints = 0)
        void missingCoverIdHasNoUrl(final Integer coverId) {
            final String coverUrl = OpenLibraryMapper.buildCoverUrl(coverId);

            assertThat(coverUrl).isNull();
        }
    }

    @Nested
    @DisplayName("stripWorksPrefix")
    class StripWorksPrefix {

        @Test
        void prefixStripped() {
            final String workKey = OpenLibraryMapper.stripWorksPrefix("/works/" + HOBBIT_WORK_KEY);

            assertThat(workKey).isEqualTo(HOBBIT_WORK_KEY);
        }

        @Test
        void bareKeyUnchanged() {
            final String workKey = OpenLibraryMapper.stripWorksPrefix(HOBBIT_WORK_KEY);

            assertThat(workKey).isEqualTo(HOBBIT_WORK_KEY);
        }
    }
}
