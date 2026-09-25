package com.betterreads.integration.hardcover;

import com.betterreads.integration.Fixtures;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class SeriesBooksJson {

    private static final String BOOK_SERIES = "book_series";

    private static final String POSITION = "position";

    private static final String BOOK = "book";

    private static final String TITLE = "title";

    private static final int GRAPHIC_NOVEL_CATEGORY = 4;

    private final ObjectNode json = Fixtures.json("hardcover/series-books.json");

    private final ObjectNode template = ((ObjectNode) series().path(BOOK_SERIES).get(0)).deepCopy();

    private SeriesBooksJson() {
    }

    public static SeriesBooksJson seriesBooks() {
        return new SeriesBooksJson();
    }

    public SeriesBooksJson withName(final String name) {
        series().put("name", name);
        return this;
    }

    public SeriesBooksJson withBookCount(final int count) {
        series().put("primary_books_count", count);
        return this;
    }

    public SeriesBooksJson withoutVolumes() {
        series().putArray(BOOK_SERIES);
        return this;
    }

    public SeriesBooksJson withVolume(final int position, final String title, final String description) {
        final ObjectNode volume = template.deepCopy().put(POSITION, position);
        volume.withObject(BOOK).put(TITLE, title).put("description", description);
        volumes().add(volume);
        return this;
    }

    public SeriesBooksJson withComicVolume(final String title, final boolean compilation) {
        final ObjectNode volume = template.deepCopy().put(POSITION, 1);
        volume.withObject(BOOK).put(TITLE, title)
            .put("book_category_id", GRAPHIC_NOVEL_CATEGORY).put("compilation", compilation);
        volumes().add(volume);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }

    private ObjectNode series() {
        return (ObjectNode) json.at("/data/series/0");
    }

    private ArrayNode volumes() {
        return series().withArray(BOOK_SERIES);
    }
}
