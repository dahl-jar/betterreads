package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.book.FieldEvidence;
import com.betterreads.book.VerifiedField;
import com.betterreads.book.VerifiedMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;

class CitationCheckTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static final String SOURCE = MetadataJson.SOURCE;

    private static final int YEAR = MetadataJson.YEAR;

    private static final String YEAR_QUOTE = "first published in 2014 by Del Rey";

    private static final String KEPT_QUOTE = "toils beneath the surface of Mars";

    private static final String PAGE_BLURB = "Darrow’s people are slaves. He joins a rebellion, "
        + "infiltrates the ruling Golds and fights to free Mars from their rule.";

    private static final String YEAR_PAGE = "Red Rising was " + YEAR_QUOTE + ".";

    private static final String HEADING = "Red Rising by Pierce Brown";

    private static final String SIBLING = "Golden Son (Red Rising Saga #2) by Pierce Brown";

    private static final String BLURB_START = "Darrow's people";

    private static final String BLURB_END = "from their rule.";

    private static final String SERIES_QUOTE = "Red Rising is a standalone novel";

    private static final String OFF_TOPIC = "Menu. Sign in. The best books of the year.";

    private static final String OFF_TOPIC_QUOTE = "the best books";

    private static final int REVIEW_LINES = 150;

    private static final int YEAR_PREFIX = 201;

    private static final int NEGATIVE_YEAR = -50;

    private static final double HALF_STEP = 1.5;

    private static final String NOTES = "Notes. ";

    private static final String FABRICATED = "Fabricated Title";

    private static final String LAST_LINE = "The end.";

    private static final String READERS = "Readers loved it. ";

    private static final int MAX_CUT = 2400;

    private static final int LAST_IN_RANGE = 5;

    private static final String RANGE_QUOTE = "Red Rising Saga books 2-5";

    private static final String SAGA_QUOTE = "Red Rising Saga #1";

    private static final String UNIVERSE_QUOTE = "Part of the Red Rising Universe #4";

    private static final String NO_DESCRIPTION = "Red Rising has no publisher description";

    private static final String SEARCH = "https://www.goodreads.com/search?q=Fabricated+Title";

    private static final String RESULTS = "Results for " + FABRICATED;

    private static final String ISBN10_QUOTE = "ISBN 0-345-53978-8";

    private static final String A_NOVEL = "Red Rising, a novel";

    private final SourcePageFetcher fetcher = mock(SourcePageFetcher.class);

    private final CitationCheck check = new CitationCheck(fetcher);

    private final MetadataCheckRequest book = MetadataJson.request(MetadataJson.BOOK_ID);

    private void givenPage(final String text) {
        givenPage(text, text);
    }

    private void givenPage(final String heading, final String text) {
        when(fetcher.page(anyString())).thenReturn(Optional.of(new SourcePage(SOURCE, heading, text)));
    }

    private static FieldVerdict verdict(final CheckedField field, final JsonNode value, final String quote) {
        return new FieldVerdict(field, VerdictStatus.CORRECTED, value, quote, SOURCE);
    }

    private static FieldVerdict yearVerdict(final VerdictStatus status, final String quote) {
        return new FieldVerdict(CheckedField.YEAR, status, IntNode.valueOf(YEAR), quote, SOURCE);
    }

    private static DescriptionVerdict description(
        final DescriptionStatus status, final String start, final String end, final String quote) {
        return new DescriptionVerdict(status, SOURCE, start, end, quote);
    }

    private CheckedBook verifyFields(final MetadataCheckRequest asked, final FieldVerdict... fields) {
        return check.verify(new FieldVerdicts(List.of(fields), DescriptionVerdict.NONE, Map.of(),
            JSON.createObjectNode()), asked);
    }

    private CheckedBook verifyField(final FieldVerdict verdict) {
        return verifyFields(MetadataJson.request(MetadataJson.BOOK_ID), verdict);
    }

    private CheckedBook verifyDescription(final MetadataCheckRequest asked, final DescriptionVerdict description) {
        return check.verify(new FieldVerdicts(List.of(), description, Map.of(), JSON.createObjectNode()), asked);
    }

    private static JsonNode series(final String name, final double number) {
        return JSON.createObjectNode().put("name", name).put("number", number);
    }

    private static JsonNode names(final String... names) {
        return JSON.valueToTree(List.of(names));
    }

    @Nested
    class Fields {

        @Test
        void shouldApplyACorrectionWhoseQuoteIsOnThePage() {
            givenPage(YEAR_PAGE);

            final CheckedBook checked = verifyField(yearVerdict(VerdictStatus.CORRECTED, YEAR_QUOTE));

            assertThat(checked.metadata().year()).isEqualTo(YEAR);
            assertThat(checked.metadata().evidence())
                .containsEntry(VerifiedField.YEAR, new FieldEvidence(SOURCE, YEAR_QUOTE));
        }

        @Test
        void shouldRejectACorrectionWhoseQuoteIsMissing() {
            givenPage("Red Rising is a science fiction novel.");

            final CheckedBook checked = verifyField(yearVerdict(VerdictStatus.CORRECTED, YEAR_QUOTE));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.YEAR_FIELD, FieldOutcome.QUOTE_NOT_ON_PAGE);
        }

        @Test
        void shouldRejectWhenThePageIsUnreachable() {
            when(fetcher.page(anyString())).thenReturn(Optional.empty());

            final CheckedBook checked = verifyField(yearVerdict(VerdictStatus.CORRECTED, YEAR_QUOTE));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.YEAR_FIELD, FieldOutcome.PAGE_UNREACHABLE);
        }

        @Test
        void shouldFetchEachUrlOncePerBatch() {
            givenPage(YEAR_PAGE);
            verifyField(yearVerdict(VerdictStatus.CONFIRMED, YEAR_QUOTE));

            verifyFields(MetadataJson.request(MetadataJson.OTHER_ID), yearVerdict(VerdictStatus.CONFIRMED, YEAR_QUOTE));

            verify(fetcher, times(1)).page(SOURCE);
        }

        @ParameterizedTest
        @MethodSource("com.betterreads.clients.websearch.CitationCheckTest#absentValues")
        void shouldRejectAValueAbsentFromItsQuote(final FieldVerdict verdict) {
            givenPage(HEADING, NOTES + verdict.quote() + ".");

            final CheckedBook checked = verifyField(verdict);

            assertThat(checked.outcomes()).containsEntry(verdict.field().key(), FieldOutcome.VALUE_NOT_IN_QUOTE);
        }
    }

    @Nested
    class Bindings {

        @Test
        void shouldAcceptAnIsbn10InTheQuote() {
            final String quote = ISBN10_QUOTE;
            givenPage(HEADING, NOTES + quote + ".");

            final CheckedBook checked = verifyField(
                verdict(CheckedField.ISBN, StringNode.valueOf(MetadataJson.ISBN), quote));

            assertThat(checked.metadata().isbn13()).isEqualTo(MetadataJson.ISBN);
        }

        @Test
        void shouldRejectAnIsbnFromAPageAboutAnotherBook() {
            final String quote = ISBN10_QUOTE;
            givenPage(SIBLING, NOTES + quote + ".");

            final CheckedBook checked = verifyField(
                verdict(CheckedField.ISBN, StringNode.valueOf(MetadataJson.ISBN), quote));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.ISBN_FIELD, FieldOutcome.TITLE_NOT_ON_PAGE);
        }

        @Test
        void shouldRejectATitleFromAPageWithoutTheStoredIsbn() {
            final String quote = "Golden Son by Pierce Brown";
            givenPage(SIBLING, quote);

            final CheckedBook checked = verifyField(
                verdict(CheckedField.TITLE, StringNode.valueOf(MetadataJson.GOLDEN_SON), quote));

            assertThat(checked.outcomes())
                .containsEntry(MetadataJson.TITLE_FIELD, FieldOutcome.TITLE_SOURCE_WITHOUT_ISBN);
        }

        @Test
        void shouldAcceptATitleFromAPageWithTheStoredIsbn() {
            final String quote = A_NOVEL;
            givenPage(HEADING, quote + ". ISBN " + MetadataJson.ISBN);

            final CheckedBook checked = verifyField(
                verdict(CheckedField.TITLE, StringNode.valueOf(MetadataJson.TITLE), quote));

            assertThat(checked.metadata().title()).isEqualTo(MetadataJson.TITLE);
        }

        @Test
        void shouldKeepTheStoredNumberWhenTheQuoteNamesOnlyTheSeries() {
            final String quote = "the first Red Rising Saga novel";
            givenPage(NOTES + quote + ".");

            final CheckedBook checked = verifyField(
                verdict(CheckedField.SERIES, series(MetadataJson.SERIES, 1), quote));

            assertThat(checked.metadata().seriesName()).isEqualTo(MetadataJson.SERIES);
        }

        @Test
        void shouldRejectAUniverseWhenTheSeriesFails() {
            givenPage(NOTES + UNIVERSE_QUOTE + ".");

            final CheckedBook checked = verifyFields(MetadataJson.request(MetadataJson.BOOK_ID),
                verdict(CheckedField.SERIES, series(MetadataJson.SERIES, 1), SAGA_QUOTE),
                verdict(CheckedField.UNIVERSE, series(MetadataJson.UNIVERSE, 4), UNIVERSE_QUOTE));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.UNIVERSE_FIELD, FieldOutcome.VALUE_REJECTED);
            assertThat(checked.metadata().universe()).isNull();
        }

        @Test
        void shouldRejectAQuoteThatEchoesTheCitedUrl() {
            final SourcePage results = new SourcePage(SEARCH, FABRICATED, RESULTS);
            when(fetcher.page(SEARCH)).thenReturn(Optional.of(results));

            final CheckedBook checked = verifyField(new FieldVerdict(CheckedField.TITLE,
                VerdictStatus.CORRECTED, StringNode.valueOf(FABRICATED), RESULTS, SEARCH));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.TITLE_FIELD, FieldOutcome.QUOTE_IN_URL);
        }

        @Test
        void shouldRejectAnAuthorEchoedByTheCitedUrl() {
            final String search = "https://www.goodreads.com/search?q=Pierce+Brown";
            final String quote = "Written by Pierce Brown";
            when(fetcher.page(search)).thenReturn(Optional.of(new SourcePage(search, HEADING, quote)));

            final CheckedBook checked = verifyField(new FieldVerdict(CheckedField.AUTHORS,
                VerdictStatus.CONFIRMED, names(MetadataJson.AUTHOR), quote, search));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.AUTHORS_FIELD, FieldOutcome.QUOTE_IN_URL);
        }

        @Test
        void shouldAcceptAUniverseWithItsSeries() {
            givenPage(NOTES + SAGA_QUOTE + ". " + UNIVERSE_QUOTE + ".");

            final CheckedBook checked = verifyFields(MetadataJson.request(MetadataJson.BOOK_ID),
                verdict(CheckedField.SERIES, series(MetadataJson.SERIES, 1), SAGA_QUOTE),
                verdict(CheckedField.UNIVERSE, series(MetadataJson.UNIVERSE, 4), UNIVERSE_QUOTE));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.UNIVERSE_FIELD, FieldOutcome.ACCEPTED);
        }
    }

    @Nested
    class Clears {

        @Test
        void shouldClearASeriesWhoseQuoteNamesTheBook() {
            givenPage(NOTES + SERIES_QUOTE + ".");

            final VerifiedMetadata metadata = clearSeries(SERIES_QUOTE);

            assertThat(metadata.seriesCleared()).isTrue();
        }

        @Test
        void shouldNotClearASeriesWithAQuoteAboutAnotherBook() {
            givenPage(OFF_TOPIC);

            final VerifiedMetadata metadata = clearSeries(OFF_TOPIC_QUOTE);

            assertThat(metadata.seriesCleared()).isFalse();
        }

        @Test
        void shouldNotClearASeriesWithAQuoteNamingOnlyTheSeries() {
            givenPage(SIBLING);

            final VerifiedMetadata metadata = clearSeries(SIBLING);

            assertThat(metadata.seriesCleared()).isFalse();
        }

        @Test
        void shouldClearADescriptionWhoseQuoteNamesTheBook() {
            final String quote = NO_DESCRIPTION;
            givenPage(NOTES + quote + ".");

            final CheckedBook checked = verifyDescription(book, description(DescriptionStatus.CLEAR, "", "", quote));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.ACCEPTED);
            assertThat(checked.metadata().descriptionCleared()).isTrue();
            assertThat(checked.metadata().evidence())
                .containsEntry(VerifiedField.DESCRIPTION, new FieldEvidence(SOURCE, quote));
        }

        @Test
        void shouldNotClearADescriptionWithAQuoteAboutAnotherBook() {
            givenPage(HEADING, OFF_TOPIC);

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.CLEAR, "", "", OFF_TOPIC_QUOTE));

            assertThat(checked.outcomes())
                .containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.VALUE_NOT_IN_QUOTE);
            assertThat(checked.metadata().descriptionCleared()).isFalse();
        }

        @Test
        void shouldNotClearADescriptionWithAQuoteOffThePage() {
            givenPage(HEADING, OFF_TOPIC);

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.CLEAR, "", "", NO_DESCRIPTION));

            assertThat(checked.outcomes())
                .containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.QUOTE_NOT_ON_PAGE);
        }

        private VerifiedMetadata clearSeries(final String quote) {
            final FieldVerdict clear = new FieldVerdict(
                CheckedField.SERIES, VerdictStatus.CLEAR, series("", 0), quote, SOURCE);
            return verifyField(clear).metadata();
        }
    }

    @Nested
    class Cuts {

        @Test
        void shouldStoreTheCutDescriptionAndNotTheModelText() {
            givenPage(HEADING, PAGE_BLURB + " Other books by Pierce Brown.");

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.REPLACE, BLURB_START, BLURB_END, BLURB_START));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.ACCEPTED);
            assertThat(checked.metadata().description()).isEqualTo(PAGE_BLURB);
            assertThat(checked.metadata().evidence())
                .containsEntry(VerifiedField.DESCRIPTION, new FieldEvidence(SOURCE, BLURB_START + " ... " + BLURB_END));
        }

        @Test
        void shouldCutFromAPageAboutABookNamedLikeItsSeries() {
            givenPage(HEADING, PAGE_BLURB);
            final MetadataCheckRequest named = new MetadataCheckRequest(MetadataJson.BOOK_ID, MetadataJson.TITLE,
                List.of(MetadataJson.AUTHOR), YEAR, MetadataJson.TITLE, 1.0, MetadataJson.ISBN, null, List.of(), null);

            final CheckedBook checked = verifyDescription(named,
                description(DescriptionStatus.REPLACE, BLURB_START, BLURB_END, ""));

            assertThat(checked.metadata().description()).isEqualTo(PAGE_BLURB);
        }

        @Test
        void shouldAcceptACutAtTheMaximumLength() {
            final String opening = PAGE_BLURB + " ";
            final int room = MAX_CUT - opening.length() - LAST_LINE.length();
            final String cut = opening + READERS.repeat(REVIEW_LINES).substring(0, room) + LAST_LINE;
            givenPage(HEADING, cut);

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.REPLACE, BLURB_START, LAST_LINE, ""));

            assertThat(checked.metadata().description()).isEqualTo(cut);
        }

        @Test
        void shouldCutFromAPageThatNamesOnlyTheCoreTitle() {
            givenPage(HEADING, PAGE_BLURB);
            final MetadataCheckRequest subtitled =
                MetadataJson.request(MetadataJson.BOOK_ID, "Red Rising: Book One", 1.0, MetadataJson.ISBN);

            final CheckedBook checked = verifyDescription(subtitled,
                description(DescriptionStatus.REPLACE, BLURB_START, BLURB_END, ""));

            assertThat(checked.metadata().description()).isEqualTo(PAGE_BLURB);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Menu", SIBLING})
        void shouldRejectACutFromAPageAboutAnotherBook(final String heading) {
            givenPage(heading, PAGE_BLURB);

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.REPLACE, BLURB_START, BLURB_END, ""));

            assertThat(checked.outcomes())
                .containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.TITLE_NOT_ON_PAGE);
        }

        @Test
        void shouldReportAnUnreachableDescriptionPage() {
            when(fetcher.page(anyString())).thenReturn(Optional.empty());

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.REPLACE, BLURB_START, BLURB_END, ""));

            assertThat(checked.outcomes())
                .containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.PAGE_UNREACHABLE);
        }

        @Test
        void shouldRejectAnAnchorEchoedByTheCitedUrl() {
            final String echo = "https://en.wikipedia.org/w/index.php?search=Darrow%27s+people";
            when(fetcher.page(echo)).thenReturn(Optional.of(new SourcePage(echo, HEADING, PAGE_BLURB)));

            final CheckedBook checked = verifyDescription(book,
                new DescriptionVerdict(DescriptionStatus.REPLACE, echo, BLURB_START, BLURB_END, ""));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.QUOTE_IN_URL);
        }

        @Test
        void shouldReportAMissingAnchor() {
            givenPage(HEADING, PAGE_BLURB);

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.REPAIR, "Golden Son opens", BLURB_END, ""));

            assertThat(checked.outcomes())
                .containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.ANCHOR_NOT_FOUND);
        }

        @Test
        void shouldRejectACutUnderTheMinimumLength() {
            givenPage(HEADING, PAGE_BLURB);

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.REPLACE, BLURB_START, "are slaves.", ""));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.CUT_REJECTED);
        }

        @Test
        void shouldRejectACutOverTheMaximumLength() {
            givenPage(HEADING, PAGE_BLURB + " Reviews. " + READERS.repeat(REVIEW_LINES) + LAST_LINE);

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.REPLACE, BLURB_START, LAST_LINE, ""));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.CUT_REJECTED);
        }

        @Test
        void shouldRejectACutThatFailsTheQualityCheck() {
            givenPage(HEADING, "For use in schools and libraries only. " + PAGE_BLURB);

            final CheckedBook checked = verifyDescription(book,
                description(DescriptionStatus.REPLACE, "For use in schools", BLURB_END, ""));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.CUT_REJECTED);
        }
    }

    @Nested
    class Keeps {

        @Test
        void shouldKeepADescriptionWhoseQuoteIsInTheStoredText() {
            givenPage(HEADING, MetadataJson.DESCRIPTION);

            final CheckedBook checked = verifyDescription(book, keep(KEPT_QUOTE));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.KEPT);
            assertThat(checked.metadata().description()).isEqualTo(MetadataJson.DESCRIPTION);
            assertThat(checked.metadata().evidence())
                .containsEntry(VerifiedField.DESCRIPTION, new FieldEvidence(SOURCE, KEPT_QUOTE));
        }

        @Test
        void shouldNotKeepADescriptionWhoseQuoteIsMissingFromTheStoredText() {
            final String otherQuote = "a Red who joins the Sons of Ares";
            givenPage(HEADING, otherQuote);

            final CheckedBook checked = verifyDescription(book, keep(otherQuote));

            assertThat(checked.outcomes())
                .containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.QUOTE_NOT_STORED);
        }

        @Test
        void shouldNotKeepWithAQuoteOfAFewWords() {
            givenPage(HEADING, MetadataJson.DESCRIPTION);

            final CheckedBook checked = verifyDescription(book, keep("the surface"));

            assertThat(checked.outcomes()).containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.QUOTE_TOO_SHORT);
        }

        @Test
        void shouldNotKeepFromAPageAboutAnotherBook() {
            givenPage(SIBLING, MetadataJson.DESCRIPTION);

            final CheckedBook checked = verifyDescription(book, keep(KEPT_QUOTE));

            assertThat(checked.outcomes())
                .containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.TITLE_NOT_ON_PAGE);
        }

        @Test
        void shouldNotKeepWhenTheQuoteIsOffThePage() {
            givenPage(HEADING, "Menu.");

            final CheckedBook checked = verifyDescription(book, keep(KEPT_QUOTE));

            assertThat(checked.outcomes())
                .containsEntry(MetadataJson.DESCRIPTION_FIELD, FieldOutcome.QUOTE_NOT_ON_PAGE);
        }

        private static DescriptionVerdict keep(final String quote) {
            return description(DescriptionStatus.KEEP, "", "", quote);
        }
    }

    static Stream<Arguments> absentValues() {
        return Stream.of(
            Arguments.of(verdict(CheckedField.TITLE, StringNode.valueOf("Golden Son"), A_NOVEL)),
            Arguments.of(verdict(CheckedField.AUTHORS, names("Mallory Brown"), "By Pierce Brown")),
            Arguments.of(verdict(CheckedField.YEAR, IntNode.valueOf(YEAR_PREFIX), YEAR_QUOTE)),
            Arguments.of(verdict(CheckedField.YEAR, IntNode.valueOf(NEGATIVE_YEAR), "Over 50 translations")),
            Arguments.of(verdict(CheckedField.ISBN, StringNode.valueOf(MetadataJson.ISBN), "ISBN 9780345539793")),
            Arguments.of(verdict(CheckedField.SERIES, series("Golden Saga", 1), "Book 1 of the Red Rising Saga")),
            Arguments.of(verdict(CheckedField.SERIES, series(MetadataJson.SERIES, 2),
                "Book 12 of the Red Rising Saga")),
            Arguments.of(verdict(CheckedField.SERIES, series(MetadataJson.SERIES, HALF_STEP),
                "Red Rising Saga books 1-5")),
            Arguments.of(verdict(CheckedField.SERIES, series(MetadataJson.SERIES, 2), RANGE_QUOTE)),
            Arguments.of(verdict(CheckedField.SERIES, series(MetadataJson.SERIES, LAST_IN_RANGE), RANGE_QUOTE)),
            Arguments.of(verdict(CheckedField.YEAR, IntNode.valueOf(-NEGATIVE_YEAR), "first told in -50")));
    }
}
