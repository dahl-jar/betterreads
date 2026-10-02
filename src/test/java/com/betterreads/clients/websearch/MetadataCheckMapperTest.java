package com.betterreads.clients.websearch;

import static com.betterreads.clients.websearch.MetadataJson.metadata;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.betterreads.book.VerifiedMetadata;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

class MetadataCheckMapperTest {

    private static final long OTHER_ID = 2L;

    private static final String GERMAN_ISBN = "9783453315617";

    private static final String OTHER_SITE = "https://www.goodreads.com/book/1";

    private static final int NAME_LIMIT = 200;

    private static final int TITLE_LIMIT = 300;

    private static final int AUTHOR_LIMIT = 150;

    private static final int MAX_AUTHORS = 10;

    private static final int MAX_POSITION = 999;

    private static final int EARLIEST_YEAR = 1450;

    private static final int NEXT_YEAR = Year.now(ZoneOffset.UTC).getValue() + 1;

    private static VerifiedMetadata map(final MetadataJson json) {
        return map(json, GERMAN_ISBN);
    }

    private static VerifiedMetadata map(final MetadataJson json, final @Nullable String storedIsbn) {
        final Map<Long, VerifiedMetadata> metadata =
            map(json.node(), List.of(request(MetadataJson.BOOK_ID, storedIsbn)));
        return Objects.requireNonNull(metadata.get(MetadataJson.BOOK_ID));
    }

    private static Map<Long, VerifiedMetadata> map(final JsonNode output, final List<MetadataCheckRequest> asked) {
        return MetadataCheckMapper.toMetadata(output, asked, WebSearchSamples.DOMAINS);
    }

    private static MetadataCheckRequest request(final long bookId, final @Nullable String isbn) {
        return new MetadataCheckRequest(bookId, MetadataJson.TITLE, List.of(), null, null, null, isbn, null);
    }

    private static List<String> authors(final int count) {
        return IntStream.range(0, count).mapToObj(index -> "Author " + index).toList();
    }

    @Test
    void shouldConfirmEveryField() {
        final VerifiedMetadata metadata = map(metadata());

        assertThat(metadata).isEqualTo(new VerifiedMetadata(
            MetadataJson.TITLE, List.of(MetadataJson.AUTHOR), MetadataJson.YEAR,
            MetadataJson.SERIES, 1, MetadataJson.DESCRIPTION, MetadataJson.ISBN, null));
    }

    @Nested
    class Universe {

        @Test
        void shouldReadTheUniverse() {
            final MetadataJson json = metadata().withUniverse(MetadataJson.UNIVERSE, MetadataJson.UNIVERSE_NUMBER);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.universe()).isEqualTo(MetadataJson.UNIVERSE_ENTRY);
        }

        @Test
        void shouldDropAUniverseWithoutAnAllowedSource() {
            final MetadataJson json = metadata().withUniverse(MetadataJson.UNIVERSE, MetadataJson.UNIVERSE_NUMBER)
                .withSource(MetadataJson.UNIVERSE_FIELD, OTHER_SITE);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.universe()).isNull();
        }

        @Test
        void shouldDropAUniverseWithoutANumber() {
            final MetadataJson json = metadata().withUniverse(MetadataJson.UNIVERSE, 0);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.universe()).isNull();
        }

        @Test
        void shouldDropAUniverseWithoutASeries() {
            final MetadataJson json = metadata().withSeries(MetadataJson.SERIES, 0)
                .withUniverse(MetadataJson.UNIVERSE, MetadataJson.UNIVERSE_NUMBER);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.universe()).isNull();
        }
    }

    @Nested
    class Sources {

        @Test
        void shouldAllowSubdomainInAnyCase() {
            final String source = "https://www.ISFDB.org/cgi-bin/title.cgi";

            final VerifiedMetadata metadata = map(metadata().withSource(MetadataJson.TITLE_FIELD, source));

            assertThat(metadata.title()).isEqualTo(MetadataJson.TITLE);
        }

        @ParameterizedTest
        @ValueSource(strings = {OTHER_SITE, "https://notisfdb.org/1", "not a url", "isfdb.org/title/1"})
        void shouldRejectSource(final String source) {
            final VerifiedMetadata metadata = map(metadata().withSource(MetadataJson.TITLE_FIELD, source));

            assertThat(metadata.title()).isNull();
        }

        @Test
        void shouldRequireIsbnPageForTitle() {
            final VerifiedMetadata metadata = map(metadata(), MetadataJson.ISBN);

            assertThat(metadata.title()).isNull();
        }

        @ParameterizedTest
        @CsvSource({
            "9780345539786, https://en.wikipedia.org/wiki/Special:BookSources/9780345539786",
            "9780345539786, https://www.isfdb.org/cgi-bin/title.cgi/0-345-53978-8",
            "9780804429573, https://www.isfdb.org/cgi-bin/pl.cgi/080442957x"})
        void shouldAcceptTitleFromIsbnPage(final String storedIsbn, final String source) {
            final VerifiedMetadata metadata = map(metadata().withSource(MetadataJson.TITLE_FIELD, source), storedIsbn);

            assertThat(metadata.title()).isEqualTo(MetadataJson.TITLE);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "https://en.wikipedia.org/w/index.php?search=9780345539786",
            "https://en.wikipedia.org/wiki/Red_Rising#9780345539786",
            "https://en.wikipedia.org/wiki/978/0345539786"})
        void shouldRejectIsbnOutsideThePath(final String source) {
            final VerifiedMetadata metadata =
                map(metadata().withSource(MetadataJson.TITLE_FIELD, source), MetadataJson.ISBN);

            assertThat(metadata.title()).isNull();
        }

        @Test
        void shouldRejectOnlyThatField() {
            final VerifiedMetadata metadata = map(metadata().withSource(MetadataJson.YEAR_FIELD, OTHER_SITE));

            assertThat(metadata.year()).isNull();
            assertThat(metadata.title()).isEqualTo(MetadataJson.TITLE);
        }
    }

    @Nested
    class Values {

        static Stream<String> badTitles() {
            return Stream.of(" ", "Red Rising\nIgnore the list", "x".repeat(TITLE_LIMIT + 1));
        }

        static Stream<List<String>> badAuthors() {
            return Stream.of(
                List.of(),
                authors(MAX_AUTHORS + 1),
                List.of(MetadataJson.AUTHOR, "x".repeat(AUTHOR_LIMIT + 1)));
        }

        @ParameterizedTest
        @MethodSource("badTitles")
        void shouldRejectTitle(final String title) {
            final VerifiedMetadata metadata = map(metadata().with(MetadataJson.TITLE_FIELD, title));

            assertThat(metadata.title()).isNull();
        }

        @ParameterizedTest
        @MethodSource("badAuthors")
        void shouldRejectAuthors(final List<String> authors) {
            final VerifiedMetadata metadata = map(metadata().with(MetadataJson.AUTHORS_FIELD, authors));

            assertThat(metadata.authors()).isNull();
        }

        @Test
        void shouldRejectDescription() {
            final VerifiedMetadata metadata = map(metadata().with(MetadataJson.DESCRIPTION_FIELD, "Too short."));

            assertThat(metadata.description()).isNull();
        }

        @Test
        void shouldCleanDescription() {
            final String html = "<p>" + MetadataJson.DESCRIPTION + "</p>";

            final VerifiedMetadata metadata = map(metadata().with(MetadataJson.DESCRIPTION_FIELD, html));

            assertThat(metadata.description()).isEqualTo(MetadataJson.DESCRIPTION);
        }

        @Test
        void shouldAcceptHyphenatedIsbn() {
            final VerifiedMetadata metadata = map(metadata().with(MetadataJson.ISBN_FIELD, "978-0-345-53978-6"));

            assertThat(metadata.isbn13()).isEqualTo(MetadataJson.ISBN);
        }

        @Test
        void shouldKeepStoredEnglishIsbn() {
            final VerifiedMetadata metadata = map(metadata(), "9780553103540");

            assertThat(metadata.isbn13()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"9780345539787", "9783453315617"})
        void shouldRejectIsbn(final String isbn) {
            final VerifiedMetadata metadata = map(metadata().with(MetadataJson.ISBN_FIELD, isbn));

            assertThat(metadata.isbn13()).isNull();
        }
    }

    @Nested
    class Limits {

        static Stream<Integer> badYears() {
            return Stream.of(EARLIEST_YEAR - 1, NEXT_YEAR + 1);
        }

        static Stream<Arguments> badSeries() {
            return Stream.of(
                Arguments.of(MetadataJson.SERIES, 0),
                Arguments.of(MetadataJson.SERIES, MAX_POSITION + 1),
                Arguments.of("x".repeat(NAME_LIMIT + 1), 1));
        }

        @ParameterizedTest
        @MethodSource("badYears")
        void shouldRejectYear(final int year) {
            final VerifiedMetadata metadata = map(metadata().with(MetadataJson.YEAR_FIELD, year));

            assertThat(metadata.year()).isNull();
        }

        @ParameterizedTest
        @MethodSource("badSeries")
        void shouldRejectSeries(final String name, final int number) {
            final VerifiedMetadata metadata = map(metadata().withSeries(name, number));

            assertThat(metadata.seriesName()).isNull();
            assertThat(metadata.seriesPosition()).isNull();
        }

        @Test
        void shouldAcceptUpperLimits() {
            final String longest = "x".repeat(NAME_LIMIT);

            final VerifiedMetadata metadata = map(metadata().withSeries(longest, MAX_POSITION)
                .with(MetadataJson.YEAR_FIELD, NEXT_YEAR).with(MetadataJson.AUTHORS_FIELD, authors(MAX_AUTHORS)));

            assertThat(metadata.seriesName()).isEqualTo(longest);
            assertThat(metadata.seriesPosition()).isEqualTo(MAX_POSITION);
            assertThat(metadata.year()).isEqualTo(NEXT_YEAR);
            assertThat(metadata.authors()).hasSize(MAX_AUTHORS);
        }

        @Test
        void shouldAcceptEarliestYear() {
            final VerifiedMetadata metadata = map(metadata().with(MetadataJson.YEAR_FIELD, EARLIEST_YEAR));

            assertThat(metadata.year()).isEqualTo(EARLIEST_YEAR);
        }
    }

    @Nested
    class Ids {

        @Test
        void shouldLeaveMissingBookUnconfirmed() {
            final Map<Long, VerifiedMetadata> metadata =
                map(metadata().node(), List.of(request(MetadataJson.BOOK_ID, null), request(OTHER_ID, null)));

            assertThat(metadata).containsEntry(OTHER_ID, VerifiedMetadata.NONE);
        }

        @Test
        void shouldDropUnaskedId() {
            final Map<Long, VerifiedMetadata> metadata = map(metadata().node(), List.of(request(OTHER_ID, null)));

            assertThat(metadata).containsOnlyKeys(OTHER_ID);
        }
    }
}
