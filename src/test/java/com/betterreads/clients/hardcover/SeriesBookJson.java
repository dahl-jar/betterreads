package com.betterreads.clients.hardcover;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class SeriesBookJson {

    private static final JsonMapper JSON = new JsonMapper();

    private static final String LANGUAGE = "language";

    private static final int GRAPHIC_NOVEL_CATEGORY = 4;

    private final double volumePosition;

    private final ObjectNode bookFields = JSON.createObjectNode();

    private SeriesBookJson(final double position, final String title) {
        this.volumePosition = position;
        bookFields.put("title", title);
    }

    public static SeriesBookJson book(final double position, final String title) {
        return new SeriesBookJson(position, title);
    }

    public SeriesBookJson id(final long id) {
        bookFields.put("id", id);
        return this;
    }

    public SeriesBookJson readers(final int readers) {
        bookFields.put("users_count", readers);
        return this;
    }

    public SeriesBookJson copyOf(final long canonicalId) {
        bookFields.put("canonical_id", canonicalId);
        return this;
    }

    public SeriesBookJson compilation() {
        bookFields.put("compilation", true);
        return this;
    }

    public SeriesBookJson comic() {
        bookFields.put("book_category_id", GRAPHIC_NOVEL_CATEGORY);
        return this;
    }

    public SeriesBookJson inLanguage(final String language) {
        final ObjectNode edition = bookFields.putObject("default_physical_edition");
        edition.putObject("reading_format").put("format", "Read");
        edition.putObject(LANGUAGE).put(LANGUAGE, language);
        return this;
    }

    double position() {
        return volumePosition;
    }

    ObjectNode fields() {
        return bookFields;
    }
}
