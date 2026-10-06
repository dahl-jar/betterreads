package com.betterreads.clients.websearch;

import java.util.List;
import java.util.stream.Collectors;

import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// PMD.TooManyMethods: one fluent method per answer variation the tests need.
@SuppressWarnings("PMD.TooManyMethods")
public final class MetadataJson {

    public static final long BOOK_ID = 1L;

    public static final String TITLE = "Red Rising";

    public static final String AUTHOR = "Pierce Brown";

    public static final int YEAR = 2014;

    public static final String SERIES = "Red Rising Saga";

    public static final String UNIVERSE = "Red Rising Universe";

    public static final int UNIVERSE_NUMBER = 4;

    public static final SeriesEntry UNIVERSE_ENTRY = new SeriesEntry(UNIVERSE, UNIVERSE_NUMBER);

    public static final String DESCRIPTION = "Darrow is a Red, a miner who toils beneath the surface of Mars so "
        + "that one day the planet can be made livable for future generations. When he learns the surface was "
        + "settled long ago and his people are slaves, he joins a rebellion and infiltrates the ruling class.";

    public static final String DESCRIPTION_START = "Darrow is a Red, a miner";

    public static final String DESCRIPTION_END = "infiltrates the ruling class.";

    public static final String ISBN = "9780345539786";

    public static final String SOURCE = "https://en.wikipedia.org/wiki/Red_Rising";

    public static final long OTHER_ID = 2L;

    public static final String GOLDEN_SON = "Golden Son";

    public static final String GERMAN_ISBN = "9783453315617";

    public static final String TITLE_FIELD = "title";

    public static final String AUTHORS_FIELD = "authors";

    public static final String YEAR_FIELD = "year";

    public static final String SERIES_FIELD = "series";

    public static final String UNIVERSE_FIELD = "universe";

    public static final String DESCRIPTION_FIELD = "description";

    public static final String ISBN_FIELD = "isbn13";

    private static final JsonMapper JSON = new JsonMapper();

    private static final String VALUE = "value";

    private static final String SOURCE_KEY = "source";

    private static final String QUOTE = "quote";

    private static final String STATUS = "status";

    private static final String NUMBER = "number";

    private static final String NAME = "name";

    private static final String NOT_FOUND = "not_found";

    private static final String CONFIRMED = "confirmed";

    private final ObjectNode root = JSON.createObjectNode();

    private final ObjectNode book;

    private MetadataJson() {
        book = root.putArray("books").addObject().put("id", BOOK_ID);
        with(TITLE_FIELD, TITLE);
        with(AUTHORS_FIELD, List.of(AUTHOR));
        with(YEAR_FIELD, YEAR);
        withSeries(SERIES, 1);
        with(ISBN_FIELD, ISBN);
        book.putObject(UNIVERSE_FIELD).put(STATUS, NOT_FOUND).put(QUOTE, "").put(SOURCE_KEY, "")
            .putObject(VALUE).put(NAME, "").put(NUMBER, 0);
        book.putObject(DESCRIPTION_FIELD).put(STATUS, "replace").put("reason", "").put(SOURCE_KEY, SOURCE)
            .put("start", DESCRIPTION_START).put("end", DESCRIPTION_END).put(QUOTE, "");
    }

    static MetadataJson metadata() {
        return new MetadataJson();
    }

    public static MetadataCheckRequest request(final long bookId) {
        return request(bookId, ISBN);
    }

    public static MetadataCheckRequest request(final long bookId, final @Nullable String isbn) {
        return request(bookId, TITLE, 1.0, isbn);
    }

    public static MetadataCheckRequest request(
        final long bookId, final String title, final double position, final @Nullable String isbn) {
        return new MetadataCheckRequest(bookId, title, List.of(AUTHOR), YEAR, SERIES, position, isbn, null,
            List.of(), DESCRIPTION);
    }

    static MetadataCheckRequest inSeries(
        final MetadataCheckRequest book, final @Nullable SeriesEntry universe, final List<SeriesBook> others) {
        return new MetadataCheckRequest(book.bookId(), book.title(), book.authors(), book.year(), book.seriesName(),
            book.seriesPosition(), book.isbn13(), universe, others, book.description());
    }

    static SourcePage sourcePage() {
        final String page = metadata().page();
        return new SourcePage(SOURCE, page, page);
    }

    public static JsonNode answer() {
        return metadata().book.deepCopy();
    }

    MetadataJson without(final String field) {
        ((ObjectNode) book.get(field)).put(STATUS, NOT_FOUND);
        return this;
    }

    MetadataJson with(final String field, final @Nullable Object value) {
        final JsonNode node = JSON.valueToTree(value);
        field(field).set(VALUE, node);
        ((ObjectNode) book.get(field)).put(QUOTE, "The book reads " + text(node) + ".");
        return this;
    }

    MetadataJson withStatus(final String field, final String status) {
        ((ObjectNode) book.get(field)).put(STATUS, status);
        return this;
    }

    MetadataJson withId(final String id) {
        book.put("id", id);
        return this;
    }

    MetadataJson withSeries(final String name, final Number number) {
        putSeries(SERIES_FIELD, name, number);
        return this;
    }

    MetadataJson withUniverse(final String name, final int number) {
        putSeries(UNIVERSE_FIELD, name, number);
        return this;
    }

    MetadataJson withSource(final String field, final @Nullable String source) {
        ((ObjectNode) book.get(field)).put(SOURCE_KEY, source);
        return this;
    }

    ObjectNode node() {
        return root;
    }

    String page() {
        final String quotes = book.valueStream()
            .map(field -> field.path(QUOTE).asString(""))
            .filter(quote -> !quote.isEmpty())
            .collect(Collectors.joining(" "));
        return "Menu. " + quotes + " " + DESCRIPTION + " Other books by Pierce Brown.";
    }

    private ObjectNode field(final String name) {
        final JsonNode existing = book.get(name);
        if (existing instanceof ObjectNode field) {
            return field;
        }
        return book.putObject(name).put(STATUS, CONFIRMED).put(SOURCE_KEY, SOURCE);
    }

    private void putSeries(final String name, final String series, final Number number) {
        final ObjectNode field = field(name).put(STATUS, CONFIRMED).put(SOURCE_KEY, SOURCE)
            .put(QUOTE, "Part of the " + series + " #" + number + ".");
        field.putObject(VALUE).put(NAME, series).set(NUMBER, JSON.valueToTree(number));
    }

    private static String text(final JsonNode node) {
        if (node.isArray()) {
            return node.valueStream().map(JsonNode::asString).collect(Collectors.joining(" and "));
        }
        return node.isNull() ? "" : node.asString();
    }
}
