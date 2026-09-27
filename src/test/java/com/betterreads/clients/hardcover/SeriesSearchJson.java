package com.betterreads.clients.hardcover;

import com.betterreads.clients.Fixtures;
import tools.jackson.databind.node.ObjectNode;

public final class SeriesSearchJson {

    private static final String NAME = "name";

    private final ObjectNode json = Fixtures.parse("""
        {"data": {"search": {"results": {"hits": [
          {"document": {"id": "404", "name": "Wheel of Time Parody",
            "author_name": "Imposter", "primary_books_count": 2, "readers_count": 10}},
          {"document": {"id": "1097", "name": "The Wheel of Time",
            "author_name": "Robert Jordan", "primary_books_count": 3, "readers_count": 23085}}
        ]}}}}
        """);

    private SeriesSearchJson() {
    }

    public static SeriesSearchJson seriesSearch() {
        return new SeriesSearchJson();
    }

    public SeriesSearchJson withSeries(final String name, final String author) {
        pickedHit().put(NAME, name).put("author_name", author);
        return this;
    }

    public SeriesSearchJson withId(final String id) {
        pickedHit().put("id", id);
        return this;
    }

    public SeriesSearchJson withoutName() {
        pickedHit().remove(NAME);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }

    private ObjectNode pickedHit() {
        return HardcoverSearchHits.picked(json);
    }
}
