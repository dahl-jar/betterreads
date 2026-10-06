package com.betterreads.clients.websearch;

import static com.betterreads.clients.websearch.MetadataJson.metadata;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class MetadataCheckMapperTest {

    private static final long OTHER_ID = MetadataJson.OTHER_ID;

    private static final String GERMAN_ISBN = MetadataJson.GERMAN_ISBN;

    private static final String ENGLISH_ISBN = "9780553103540";

    private static final String OTHER_SITE = "https://www.goodreads.com/book/1";

    private static final int NAME_LIMIT = 200;

    private static final int TITLE_LIMIT = 300;

    private static final int AUTHOR_LIMIT = 150;

    private static final int MAX_AUTHORS = 10;

    private static final int MAX_POSITION = 999;

    private static final int EARLIEST_YEAR = -3000;

    private static final int NEXT_YEAR = Year.now(ZoneOffset.UTC).getValue() + 1;

    private static VerifiedMetadata map(final MetadataJson json) {
        return map(json, GERMAN_ISBN);
    }

    private static VerifiedMetadata map(final MetadataJson json, final @Nullable String storedIsbn) {
        return check(json, storedIsbn).metadata();
    }

    private static Map<Long, VerifiedMetadata> map(final JsonNode output, final List<MetadataCheckRequest> asked) {
        final CitationCheck citations = citations(metadata().page());
        final Map<Long, MetadataCheckRequest> byId = asked.stream()
            .collect(Collectors.toMap(MetadataCheckRequest::bookId, request -> request));
        return MetadataCheckMapper.toVerdicts(output, asked, WebSearchSamples.DOMAINS).entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> citations
                .verify(entry.getValue(), Objects.requireNonNull(byId.get(entry.getKey()))).metadata()));
    }

    private static CitationCheck citations(final String page) {
        final SourcePageFetcher fetcher = mock(SourcePageFetcher.class);
        when(fetcher.page(anyString())).thenReturn(Optional.of(new SourcePage(MetadataJson.SOURCE, page, page)));
        return new CitationCheck(fetcher);
    }

    private static FieldVerdicts verdicts(final MetadataJson json) {
        final Map<Long, FieldVerdicts> verdicts = MetadataCheckMapper.toVerdicts(
            json.node(), List.of(MetadataJson.request(MetadataJson.BOOK_ID, GERMAN_ISBN)), WebSearchSamples.DOMAINS);
        return Objects.requireNonNull(verdicts.get(MetadataJson.BOOK_ID));
    }

    private static CheckedBook check(final MetadataJson json) {
        return check(json, GERMAN_ISBN);
    }

    private static CheckedBook check(final MetadataJson json, final @Nullable String storedIsbn) {
        final MetadataCheckRequest asked = MetadataJson.request(MetadataJson.BOOK_ID, storedIsbn);
        final Map<Long, FieldVerdicts> verdicts =
            MetadataCheckMapper.toVerdicts(json.node(), List.of(asked), WebSearchSamples.DOMAINS);
        final String page = storedIsbn == null ? json.page() : json.page() + " ISBN " + storedIsbn;
        return citations(page).verify(Objects.requireNonNull(verdicts.get(MetadataJson.BOOK_ID)), asked);
    }

    private static List<String> authors(final int count) {
        return IntStream.range(0, count).mapToObj(index -> "Author " + index).toList();
    }

    @Nested
    class Verdicts {

        @Test
        void shouldConfirmEveryField() {
            final MetadataJson json = metadata();

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata).usingRecursiveComparison().ignoringFields("evidence").isEqualTo(new VerifiedMetadata(
                MetadataJson.TITLE, List.of(MetadataJson.AUTHOR), MetadataJson.YEAR,
                MetadataJson.SERIES, 1.0, MetadataJson.DESCRIPTION, MetadataJson.ISBN, null));
        }

        @Test
        void shouldRejectAClearOnAFieldOtherThanSeries() {
            final MetadataJson json = metadata().withStatus(MetadataJson.TITLE_FIELD, "clear");

            final FieldVerdicts verdicts = verdicts(json);

            assertThat(verdicts.outcomes()).containsEntry(MetadataJson.TITLE_FIELD, FieldOutcome.VALUE_REJECTED);
        }

        @Test
        void shouldDropOnlyTheVerdictFromAHostOffTheList() {
            final MetadataJson json = metadata().withSource(MetadataJson.YEAR_FIELD, OTHER_SITE);

            final FieldVerdicts verdicts = verdicts(json);

            assertThat(verdicts.fields()).extracting(FieldVerdict::field)
                .doesNotContain(CheckedField.YEAR)
                .contains(CheckedField.TITLE);
            assertThat(verdicts.outcomes()).containsEntry(MetadataJson.YEAR_FIELD, FieldOutcome.SOURCE_NOT_ALLOWED);
        }

        @Test
        void shouldDropADescriptionFromAHostOffTheList() {
            final MetadataJson json = metadata().withSource(MetadataJson.DESCRIPTION_FIELD, OTHER_SITE);

            final FieldVerdicts verdicts = verdicts(json);

            assertThat(verdicts.description().status()).isEqualTo(DescriptionStatus.NOT_FOUND);
            assertThat(verdicts.outcomes())
                .containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.SOURCE_NOT_ALLOWED);
        }
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
        void shouldRejectAUniverseWithoutASeries() {
            final MetadataJson json = metadata().withSeries(MetadataJson.SERIES, 0)
                .withUniverse(MetadataJson.UNIVERSE, MetadataJson.UNIVERSE_NUMBER);

            final CheckedBook book = check(json);

            assertThat(book.metadata().universe()).isNull();
            assertThat(book.outcomes()).containsEntry(MetadataJson.UNIVERSE_FIELD, FieldOutcome.VALUE_REJECTED);
        }
    }

    @Nested
    class Sources {

        @Test
        void shouldAllowSubdomainInAnyCase() {
            final String source = "https://www.ISFDB.org/cgi-bin/title.cgi";
            final MetadataJson json = metadata().withSource(MetadataJson.TITLE_FIELD, source);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.title()).isEqualTo(MetadataJson.TITLE);
        }

        @ParameterizedTest
        @ValueSource(strings = {OTHER_SITE, "https://notisfdb.org/1", "not a url", "isfdb.org/title/1"})
        void shouldRejectSource(final String source) {
            final MetadataJson json = metadata().withSource(MetadataJson.TITLE_FIELD, source);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.title()).isNull();
        }

        @Test
        void shouldRequireIsbnPageForTitle() {
            final MetadataJson json = metadata();

            final CheckedBook book = check(json, MetadataJson.ISBN);

            assertThat(book.metadata().title()).isNull();
            assertThat(book.outcomes()).containsEntry(MetadataJson.TITLE_FIELD, FieldOutcome.TITLE_SOURCE_WITHOUT_ISBN);
        }

        @ParameterizedTest
        @CsvSource({
            "9780345539786, https://en.wikipedia.org/wiki/Special:BookSources/9780345539786",
            "9780345539786, https://www.isfdb.org/cgi-bin/title.cgi/0-345-53978-8",
            "9780804429573, https://www.isfdb.org/cgi-bin/pl.cgi/080442957x"})
        void shouldAcceptTitleFromIsbnPage(final String storedIsbn, final String source) {
            final MetadataJson json = metadata().withSource(MetadataJson.TITLE_FIELD, source);

            final VerifiedMetadata metadata = map(json, storedIsbn);

            assertThat(metadata.title()).isEqualTo(MetadataJson.TITLE);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "https://en.wikipedia.org/w/index.php?search=9780345539786",
            "https://en.wikipedia.org/wiki/Red_Rising#9780345539786",
            "https://en.wikipedia.org/wiki/978/0345539786"})
        void shouldRejectIsbnOutsideThePath(final String source) {
            final MetadataJson json = metadata().withSource(MetadataJson.TITLE_FIELD, source);

            final VerifiedMetadata metadata = map(json, MetadataJson.ISBN);

            assertThat(metadata.title()).isNull();
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
            final MetadataJson json = metadata().with(MetadataJson.TITLE_FIELD, title);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.title()).isNull();
        }

        @ParameterizedTest
        @MethodSource("badAuthors")
        void shouldRejectAuthors(final List<String> authors) {
            final MetadataJson json = metadata().with(MetadataJson.AUTHORS_FIELD, authors);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.authors()).isNull();
        }

        @Test
        void shouldAcceptHyphenatedIsbn() {
            final MetadataJson json = metadata().with(MetadataJson.ISBN_FIELD, "978-0-345-53978-6");

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.isbn13()).isEqualTo(MetadataJson.ISBN);
        }

        @Test
        void shouldKeepStoredEnglishIsbn() {
            final MetadataJson json = metadata();

            final CheckedBook book = check(json, ENGLISH_ISBN);

            assertThat(book.metadata().isbn13()).isNull();
            assertThat(book.outcomes()).containsEntry(MetadataJson.ISBN_FIELD, FieldOutcome.VALUE_REJECTED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"9780345539787", "9783453315617"})
        void shouldRejectIsbn(final String isbn) {
            final MetadataJson json = metadata().with(MetadataJson.ISBN_FIELD, isbn);

            final VerifiedMetadata metadata = map(json);

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
            final MetadataJson json = metadata().with(MetadataJson.YEAR_FIELD, year);

            final CheckedBook book = check(json);

            assertThat(book.metadata().year()).isNull();
            assertThat(book.outcomes()).containsEntry(MetadataJson.YEAR_FIELD, FieldOutcome.VALUE_REJECTED);
        }

        @ParameterizedTest
        @MethodSource("badSeries")
        void shouldRejectSeries(final String name, final int number) {
            final MetadataJson json = metadata().withSeries(name, number);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.seriesName()).isNull();
            assertThat(metadata.seriesPosition()).isNull();
        }

        @ParameterizedTest(name = "number {0} is stored as {1}")
        @CsvSource({"2.5, 2.5", "0.5, 0.5", "1.756, 1.76"})
        void shouldAcceptADecimalSeriesNumber(final double number, final double stored) {
            final MetadataJson json = metadata().withSeries(MetadataJson.SERIES, number);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.seriesPosition()).isEqualTo(stored);
        }

        @Test
        void shouldAcceptUpperLimits() {
            final String longest = "x".repeat(NAME_LIMIT);
            final MetadataJson json = metadata().withSeries(longest, MAX_POSITION)
                .with(MetadataJson.YEAR_FIELD, NEXT_YEAR).with(MetadataJson.AUTHORS_FIELD, authors(MAX_AUTHORS));

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.seriesName()).isEqualTo(longest);
            assertThat(metadata.seriesPosition()).isEqualTo((double) MAX_POSITION);
            assertThat(metadata.year()).isEqualTo(NEXT_YEAR);
            assertThat(metadata.authors()).hasSize(MAX_AUTHORS);
        }

        @Test
        void shouldAcceptEarliestYear() {
            final MetadataJson json = metadata().with(MetadataJson.YEAR_FIELD, EARLIEST_YEAR);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.year()).isEqualTo(EARLIEST_YEAR);
        }

        @Test
        void shouldRejectAYearThatIsNotAWholeNumber() {
            final MetadataJson json = metadata().with(MetadataJson.YEAR_FIELD, 2014.5);

            final VerifiedMetadata metadata = map(json);

            assertThat(metadata.year()).isNull();
        }
    }

    @Nested
    class Outcomes {

        @Test
        void shouldMarkAMissingFieldAsNotAnswered() {
            final MetadataJson json = metadata().without(MetadataJson.YEAR_FIELD);

            final CheckedBook book = check(json);

            assertThat(book.outcomes()).containsEntry(MetadataJson.YEAR_FIELD, FieldOutcome.NOT_ANSWERED);
        }

        @Test
        void shouldMarkANullValueAsNotAnswered() {
            final MetadataJson json = metadata().with(MetadataJson.YEAR_FIELD, null);

            final CheckedBook book = check(json);

            assertThat(book.metadata().year()).isNull();
            assertThat(book.outcomes()).containsEntry(MetadataJson.YEAR_FIELD, FieldOutcome.NOT_ANSWERED);
        }

        @Test
        void shouldMarkASeriesWithoutANameAsNotAnswered() {
            final MetadataJson json = metadata().withSeries("", 1);

            final FieldVerdicts verdicts = verdicts(json);

            assertThat(verdicts.outcomes()).containsEntry(MetadataJson.SERIES_FIELD, FieldOutcome.NOT_ANSWERED);
        }

        @Test
        void shouldMarkAnUnfoundDescriptionFromAnUnlistedHostAsNotAnswered() {
            final MetadataJson json = metadata().withSource(MetadataJson.DESCRIPTION_FIELD, OTHER_SITE)
                .without(MetadataJson.DESCRIPTION_FIELD);

            final FieldVerdicts verdicts = verdicts(json);

            assertThat(verdicts.outcomes()).containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.NOT_ANSWERED);
        }
    }

    @Nested
    class Ids {

        @Test
        void shouldLeaveOutABookTheAnswerSkips() {
            final List<MetadataCheckRequest> asked =
                List.of(MetadataJson.request(MetadataJson.BOOK_ID, null), MetadataJson.request(OTHER_ID, null));

            final Map<Long, VerifiedMetadata> metadata = map(metadata().node(), asked);

            assertThat(metadata).containsOnlyKeys(MetadataJson.BOOK_ID);
        }

        @Test
        void shouldDropUnaskedId() {
            final List<MetadataCheckRequest> asked = List.of(MetadataJson.request(OTHER_ID, null));

            final Map<Long, VerifiedMetadata> metadata = map(metadata().node(), asked);

            assertThat(metadata).isEmpty();
        }

        @Test
        void shouldIgnoreAnAnswerWithoutANumericId() {
            final JsonNode answer = metadata().withId("Red Rising").node();

            final Map<Long, VerifiedMetadata> metadata = map(answer, List.of(MetadataJson.request(0, null)));

            assertThat(metadata).isEmpty();
        }

        @Test
        void shouldKeepTheFirstAnswerForARepeatedId() {
            final ObjectNode answer = metadata().node();
            final ArrayNode answered = (ArrayNode) answer.get("books");
            final ObjectNode repeated = (ObjectNode) answered.get(0).deepCopy();
            ((ObjectNode) repeated.get(MetadataJson.TITLE_FIELD)).put("value", MetadataJson.GOLDEN_SON);
            answered.add(repeated);

            final Map<Long, VerifiedMetadata> metadata =
                map(answer, List.of(MetadataJson.request(MetadataJson.BOOK_ID, null)));

            assertThat(metadata.get(MetadataJson.BOOK_ID)).extracting(VerifiedMetadata::title)
                .isEqualTo(MetadataJson.TITLE);
        }
    }
}
