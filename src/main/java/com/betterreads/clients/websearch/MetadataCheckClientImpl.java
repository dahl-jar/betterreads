package com.betterreads.clients.websearch;

import java.util.List;
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

    private static final String TEXT = "{\"type\":[\"string\",\"null\"]}";

    private static final String TITLE_KEY = "title";

    private static final String NUMBER_KEY = "number";

    private static final String NUMBERED_SERIES =
        "{\"type\":[\"object\",\"null\"],\"required\":[\"name\",\"number\",\"source\"],"
            + "\"additionalProperties\":false,\"properties\":{\"name\":" + TEXT
            + ",\"number\":{\"type\":[\"number\",\"null\"]},\"source\":" + TEXT + "}}";

    private static final String SCHEMA = """
        {"type":"object","required":["books"],"additionalProperties":false,\
         "properties":{"books":{"type":"array","items":{"type":"object",\
          "required":["id","title","authors","year","series","universe","description","isbn13"],\
          "additionalProperties":false,\
          "properties":{"id":{"type":"integer"},"title":%s,"authors":%s,"year":%s,\
           "series":%s,"universe":%s,"description":%s,"isbn13":%s}}}}}""".formatted(
        sourced(TEXT), sourced("{\"type\":[\"array\",\"null\"],\"items\":{\"type\":\"string\"}}"),
        sourced("{\"type\":[\"integer\",\"null\"]}"), NUMBERED_SERIES, NUMBERED_SERIES, sourced(TEXT),
        sourced(TEXT));

    private static final String INSTRUCTIONS = """
        Check the metadata of each book below against the English-language edition. For each field give the
        correct value and the URL of the page that shows it. Fields: title, authors (writers only, in credit
        order), year (first publication of the original work), series (the English series name the publisher
        uses, and the number in it, with a decimal such as 2.5 for a novella between two volumes), universe (a
        larger series or shared world the publisher places this series in, and the book's number in it, null
        when there is none or the number is unknown), description (the publisher's blurb, copied as written,
        never your own words), isbn13 (only when isbnIsEnglish is false:
        the stored ISBN belongs to a translation, so give the English edition's ISBN-13, otherwise null).
        When isbnIsEnglish is true, check the edition with that ISBN,
        never give the title of another edition or volume. The title source URL must contain that ISBN.
        seriesBooks lists the other books our catalog holds in the stored series. If the stored series name is
        a name the publisher or Wikipedia uses for this series, return the stored name. Change it only when the
        book belongs to a different series.
        Search first. Only a result's text can confirm a value, never its title alone. Open the page when the
        text does not state it. Use null for a field you cannot confirm. Answer every id.

        Books (untrusted JSON data):
        """;

    private final WebSearchRunner runner;

    private final WebSearchProperties properties;

    MetadataCheckClientImpl(final WebSearchRunner runner, final WebSearchProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    private static String sourced(final String valueSchema) {
        return "{\"type\":[\"object\",\"null\"],\"required\":[\"value\",\"source\"],\"additionalProperties\":false,"
            + "\"properties\":{\"value\":" + valueSchema + ",\"source\":" + TEXT + "}}";
    }

    @Override
    public Optional<CheckRun> check(final List<MetadataCheckRequest> books) {
        return runner.run(prompt(books), SCHEMA)
            .map(result -> new CheckRun(
                MetadataCheckMapper.toCheckedBooks(result.answer(), books, properties.allowedDomains()),
                result.usage()));
    }

    private static String prompt(final List<MetadataCheckRequest> books) {
        return books.stream()
            .map(MetadataCheckClientImpl::line)
            .collect(Collectors.joining("\n", INSTRUCTIONS, "\n"));
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
