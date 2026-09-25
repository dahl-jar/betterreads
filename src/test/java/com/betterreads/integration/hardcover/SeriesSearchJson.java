package com.betterreads.integration.hardcover;

import com.betterreads.integration.Fixtures;
import tools.jackson.databind.node.ObjectNode;

public final class SeriesSearchJson {

    private final ObjectNode json = Fixtures.json("hardcover/series-search.json");

    private SeriesSearchJson() {
    }

    public static SeriesSearchJson seriesSearch() {
        return new SeriesSearchJson();
    }

    public SeriesSearchJson withSeries(final String name, final String author) {
        pickedHit().put("name", name).put("author_name", author);
        return this;
    }

    public SeriesSearchJson withBookCount(final int count) {
        pickedHit().put("primary_books_count", count);
        return this;
    }

    public SeriesSearchJson withoutHits() {
        SearchHits.clear(json);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }

    private ObjectNode pickedHit() {
        return SearchHits.picked(json);
    }
}
