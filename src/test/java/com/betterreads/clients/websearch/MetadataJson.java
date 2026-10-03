package com.betterreads.clients.websearch;

import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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

    private final ObjectNode root = JSON.createObjectNode();

    private final ObjectNode book;

    private MetadataJson() {
        book = root.putArray("books").addObject().put("id", BOOK_ID);
        field(TITLE_FIELD).put(VALUE, TITLE);
        field(AUTHORS_FIELD).putArray(VALUE).add(AUTHOR);
        field(YEAR_FIELD).put(VALUE, YEAR);
        putSeries(field(SERIES_FIELD), SERIES, 1);
        field(DESCRIPTION_FIELD).put(VALUE, DESCRIPTION);
        field(ISBN_FIELD).put(VALUE, ISBN);
    }

    static MetadataJson metadata() {
        return new MetadataJson();
    }

    public static JsonNode answer() {
        return metadata().book.deepCopy();
    }

    MetadataJson without(final String field) {
        book.putNull(field);
        return this;
    }

    MetadataJson with(final String field, final @Nullable Object value) {
        ((ObjectNode) book.get(field)).set(VALUE, JSON.valueToTree(value));
        return this;
    }

    MetadataJson withId(final String id) {
        book.put("id", id);
        return this;
    }

    MetadataJson withSeries(final String name, final Number number) {
        putSeries((ObjectNode) book.get(SERIES_FIELD), name, number);
        return this;
    }

    MetadataJson withUniverse(final String name, final int number) {
        putSeries(field(UNIVERSE_FIELD), name, number);
        return this;
    }

    MetadataJson withSource(final String field, final @Nullable String source) {
        ((ObjectNode) book.get(field)).put(SOURCE_KEY, source);
        return this;
    }

    ObjectNode node() {
        return root;
    }

    private ObjectNode field(final String name) {
        return book.putObject(name).put(SOURCE_KEY, SOURCE);
    }

    private static void putSeries(final ObjectNode series, final String name, final Number number) {
        series.put("name", name).set("number", JSON.valueToTree(number));
    }
}
