package com.betterreads.clients.openlibrary;

import static com.betterreads.clients.openlibrary.OpenLibrarySearchJson.HOBBIT_WORK_KEY;
import static com.betterreads.clients.openlibrary.OpenLibraryWorkJson.HOBBIT_DESCRIPTION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.betterreads.booksource.SourceBook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenLibraryMapperTest {

    private static final String LOTR_DESCRIPTION = "Originally published from 1954 through 1956.";

    private static final int FIXTURE_YEAR = 2014;

    private static final String ENGLISH = "eng";

    private static final String FRENCH = "fre";

    private static final String TITLE = "Red Rising";

    private static final String AUTHOR = "Pierce Brown";

    private static final String WORK_KEY = "/works/OL1W";

    private final OpenLibraryMapper mapper = new OpenLibraryMapper();

    @Test
    void shouldMapEveryDocAndWorkField() {
        final int coverId = 8_231_856;
        final String subtitle = "Book One";
        final OpenLibrarySearchDoc doc = new OpenLibrarySearchDoc(
            WORK_KEY, TITLE, subtitle, List.of(AUTHOR), FIXTURE_YEAR, coverId, List.of(ENGLISH));
        final OpenLibraryWork work = new OpenLibraryWork(TITLE, HOBBIT_DESCRIPTION, List.of("Fantasy"));

        final SourceBook book = Objects.requireNonNull(mapper.toSourceBook(doc, work));

        assertThat(book)
            .extracting(SourceBook::openLibraryWorkKey, SourceBook::title, SourceBook::subtitle,
                SourceBook::authorNames, SourceBook::publicationYear, SourceBook::language, SourceBook::coverUrl,
                SourceBook::description, SourceBook::rawSubjects)
            .containsExactly("OL1W", TITLE, subtitle, List.of(AUTHOR), FIXTURE_YEAR, ENGLISH,
                "https://covers.openlibrary.org/b/id/8231856-L.jpg", HOBBIT_DESCRIPTION, List.of("fantasy"));
    }

    @Test
    void shouldLeaveMissingDocFieldsNull() {
        final OpenLibrarySearchDoc doc = new OpenLibrarySearchDoc(null, TITLE, null, null, null, null, null);

        final SourceBook book = Objects.requireNonNull(mapper.toSourceBook(doc, null));

        assertThat(book.openLibraryWorkKey()).isNull();
        assertThat(book.language()).isNull();
    }

    @Nested
    @DisplayName("language")
    class Language {

        @Test
        void prefersEnglishFromMultiLanguageList() {
            final OpenLibrarySearchDoc doc = languageDoc(List.of("spa", "tur", ENGLISH, "pol"));

            final SourceBook book = mapper.toSourceBook(doc, null);

            assertThat(book)
                .isNotNull()
                .extracting(SourceBook::language)
                .isEqualTo(ENGLISH);
        }

        @Test
        void keepsFirstWhenNoEnglish() {
            final OpenLibrarySearchDoc doc = languageDoc(List.of(FRENCH, "deu"));

            final SourceBook book = mapper.toSourceBook(doc, null);

            assertThat(book)
                .isNotNull()
                .extracting(SourceBook::language)
                .isEqualTo(FRENCH);
        }

        private OpenLibrarySearchDoc languageDoc(final List<String> languages) {
            return new OpenLibrarySearchDoc(
                WORK_KEY, TITLE, null, List.of(AUTHOR), FIXTURE_YEAR, null,
                languages);
        }
    }

    @Nested
    @DisplayName("coerceDescription")
    class CoerceDescription {

        @Test
        void objectDescriptionYieldsValueText() {
            final Object description = textObject(LOTR_DESCRIPTION);

            final String coerced = OpenLibraryMapper.coerceDescription(description);

            assertThat(coerced).isEqualTo(LOTR_DESCRIPTION);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = "   ")
        void absentDescriptionIsNull(final Object description) {
            final String coerced = OpenLibraryMapper.coerceDescription(description);

            assertThat(coerced).isNull();
        }

        @Test
        void shouldReturnNullWhenObjectValueIsBlank() {
            final Object description = textObject("   ");

            final String coerced = OpenLibraryMapper.coerceDescription(description);

            assertThat(coerced).isNull();
        }

        private static Map<String, String> textObject(final String value) {
            return Map.of("type", "/type/text", "value", value);
        }
    }

    @Nested
    @DisplayName("buildCoverUrl")
    class BuildCoverUrl {

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
        void bareKeyUnchanged() {
            final String workKey = OpenLibraryMapper.stripWorksPrefix(HOBBIT_WORK_KEY);

            assertThat(workKey).isEqualTo(HOBBIT_WORK_KEY);
        }
    }
}
