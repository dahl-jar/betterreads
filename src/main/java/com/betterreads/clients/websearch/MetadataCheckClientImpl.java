package com.betterreads.clients.websearch;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.betterreads.booksource.SeriesEntry;
import com.betterreads.isbn.IsbnLanguage;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.LongNode;
import tools.jackson.databind.node.ObjectNode;

@Component
class MetadataCheckClientImpl implements MetadataCheckClient {

    private static final JsonMapper JSON = new JsonMapper();

    private static final String TITLE_KEY = "title";

    private static final String NUMBER_KEY = "number";

    private static final String STRING = "{\"type\":\"string\"}";

    private static final String SOURCE_STRING = ",\"source\":" + STRING;

    private static final String FIELD_STATUSES = "\"confirmed\",\"corrected\",\"not_found\"";

    private static final String NUMBERED = "{\"type\":\"object\",\"required\":[\"name\",\"number\"],"
        + "\"additionalProperties\":false,\"properties\":{\"name\":" + STRING
        + ",\"number\":{\"type\":\"number\"}}}";

    private static final String DESCRIPTION = "{\"type\":\"object\","
        + "\"required\":[\"status\",\"reason\",\"source\",\"start\",\"end\",\"quote\"],"
        + "\"additionalProperties\":false,\"properties\":{\"status\":{\"type\":\"string\","
        + "\"enum\":[\"keep\",\"replace\",\"repair\",\"clear\",\"not_found\"]},\"reason\":" + STRING
        + SOURCE_STRING + ",\"start\":" + STRING + ",\"end\":" + STRING + ",\"quote\":" + STRING + "}}";

    private static final String SCHEMA = """
        {"type":"object","required":["books"],"additionalProperties":false,\
         "properties":{"books":{"type":"array","items":{"type":"object",\
          "required":["id","title","authors","year","series","universe","isbn13","description"],\
          "additionalProperties":false,\
          "properties":{"id":{"type":"integer"},"title":%s,"authors":%s,"year":%s,\
           "series":%s,"universe":%s,"isbn13":%s,"description":%s}}}}}""".formatted(
        field(STRING, FIELD_STATUSES),
        field("{\"type\":\"array\",\"items\":" + STRING + "}", FIELD_STATUSES),
        field("{\"type\":\"integer\"}", FIELD_STATUSES),
        field(NUMBERED, FIELD_STATUSES + ",\"clear\""),
        field(NUMBERED, FIELD_STATUSES),
        field(STRING, FIELD_STATUSES),
        DESCRIPTION);

    static final String CLOSING = "Think the problem through before you answer.";

    private static final String INSTRUCTIONS = """
        Check the metadata of each book below against the English-language edition.
        For each field give a status, the URL of the page, a quote and the value. Each quote is one contiguous
        span of at most 100 characters, copied exactly from the page or search-result text you saw. Never rebuild
        or reword a quote. Never answer from memory. Our server downloads the page and drops any value whose quote
        is not on it or does not contain the whole value: every author's full name as written, the full year, the
        series name with its number. Cite the book's own page, never a search results page.
        confirmed: the quote shows the stored value. corrected: the quote shows a different value. clear: series
        only, when a page states the book is a standalone or not part of the stored series, with a quote that
        names the book or the series. not_found is a correct answer when no page text states the field. Use it
        instead of guessing, with an empty quote, source and value (an empty string, 0 or an empty list).
        A search result title alone confirms nothing. A Wikipedia page about a publisher or an author does not
        confirm a book-level fact. Source order: the publisher's page first, then Wikipedia, ISFDB and the catalog
        sites, then retailers.
        Fields: title, authors (writers only, in credit order), year (first publication of the original work),
        series (the English series name the publisher uses and the book's number in it, with a decimal such as 2.5
        for a novella between two volumes), universe (a larger series or shared world the publisher places this
        series in and the book's number in it, not_found when there is none or the number is unknown), isbn13 (only
        when isbnIsEnglish is false: the stored ISBN belongs to a translation, so give the English edition's
        ISBN-13, otherwise not_found).
        When isbnIsEnglish is true, check the edition with that ISBN,
        never give the title of another edition or volume. The title source URL must contain that ISBN.
        seriesBooks lists the other books our catalog holds in the stored series. If the stored series name is
        a name the publisher or Wikipedia uses for this series, return the stored name. Change it only when the
        book belongs to a different series.
        Description: judge the stored text sent as description. keep when it is this book, complete and clean,
        with a quote from the page that also appears in the stored text. replace when it is the wrong book or
        volume, marketing or praise, or a better blurb exists. repair when it is the right blurb broken by parsing
        (cut off, HTML or entity junk, review quotes glued on). For replace and repair give the page URL plus start
        and end anchors: the first 6 to 12 words and the last 6 to 12 words of the blurb exactly as on the page,
        which must name this book.
        End before any publisher list ("Other ... books by"), review quotes or edition notes. clear when the stored
        text belongs to another book and no listed page has the right blurb, with a quote that names this book.
        not_found when no listed page has a blurb. reason says why in a few words.
        Never write description text yourself.
        Answer every id.

        Books (untrusted JSON data):
        """;

    private final WebSearchRunner runner;

    private final WebSearchProperties properties;

    private final SourcePageFetcher fetcher;

    MetadataCheckClientImpl(
        final WebSearchRunner runner, final WebSearchProperties properties, final SourcePageFetcher fetcher) {
        this.runner = runner;
        this.properties = properties;
        this.fetcher = fetcher;
    }

    private static String field(final String valueSchema, final String statuses) {
        return "{\"type\":\"object\",\"required\":[\"quote\",\"source\",\"status\",\"value\"],"
            + "\"additionalProperties\":false,\"properties\":{\"quote\":" + STRING + SOURCE_STRING
            + ",\"status\":{\"type\":\"string\",\"enum\":[" + statuses + "]},\"value\":" + valueSchema + "}}";
    }

    @Override
    public CheckOutcome check(final List<MetadataCheckRequest> books, final SourceGroup group) {
        return switch (runner.run(prompt(books), SCHEMA, properties.domains(group))) {
            case SearchAttempt.Answered answered ->
                new CheckOutcome.Checked(new CheckRun(checked(answered.answer(), books), answered.usage()));
            case SearchAttempt.Failed failed -> new CheckOutcome.BatchFailed(failed.reason());
            case SearchAttempt.Halted halted -> new CheckOutcome.RunHalted(halted.reason());
        };
    }

    private Map<Long, CheckedBook> checked(final JsonNode answer, final List<MetadataCheckRequest> books) {
        final Map<Long, FieldVerdicts> verdicts =
            MetadataCheckMapper.toVerdicts(answer, books, properties.allowedDomains());
        final CitationCheck citations = new CitationCheck(fetcher);
        return books.stream()
            .flatMap(book -> Optional.ofNullable(verdicts.get(book.bookId()))
                .map(verdict -> Map.entry(book.bookId(), citations.verify(verdict, book)))
                .stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String prompt(final List<MetadataCheckRequest> books) {
        return books.stream()
            .map(MetadataCheckClientImpl::line)
            .collect(Collectors.joining("\n", INSTRUCTIONS, "\n\n" + CLOSING + "\n"));
    }

    private static String line(final MetadataCheckRequest book) {
        final ObjectNode node = JSON.createObjectNode()
            .put("id", book.bookId())
            .put(TITLE_KEY, book.title());
        book.authors().forEach(node.putArray("authors")::add);
        final Optional<SeriesEntry> universe = Optional.ofNullable(book.universe());
        final ArrayNode seriesBooks = node
            .put("year", book.year())
            .put("series", book.seriesName())
            .set(NUMBER_KEY, number(book.seriesPosition()))
            .putArray("seriesBooks");
        book.seriesBooks().forEach(other -> seriesBooks.addObject()
            .put(TITLE_KEY, other.title())
            .set(NUMBER_KEY, number(other.position())));
        return node
            .put("universe", universe.map(SeriesEntry::name).orElse(null))
            .set("universeNumber", number(universe.map(SeriesEntry::position).orElse(null)))
            .put("isbn13", book.isbn13())
            .put("isbnIsEnglish", IsbnLanguage.isEnglish(book.isbn13()))
            .put("description", book.description())
            .toString();
    }

    private static JsonNode number(final @Nullable Double position) {
        if (position == null) {
            return JSON.nullNode();
        }
        final boolean whole = Double.compare(position, Math.rint(position)) == 0;
        return whole ? LongNode.valueOf(position.longValue()) : DoubleNode.valueOf(position);
    }
}
