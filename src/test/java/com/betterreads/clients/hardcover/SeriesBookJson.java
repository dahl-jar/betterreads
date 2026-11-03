package com.betterreads.clients.hardcover;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class SeriesBookJson {

    private static final JsonMapper JSON = new JsonMapper();

    private static final String LANGUAGE = "language";

    private static final String ENGLISH = "English";

    private static final String READ_FORMAT = "Read";

    private static final String AUDIOBOOK_FORMAT = "Listened";

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
        bookFields.put("book_category_id", HardcoverCategories.GRAPHIC_NOVEL);
        return this;
    }

    public SeriesBookJson inLanguage(final String language) {
        return edition(language, READ_FORMAT, null);
    }

    public SeriesBookJson audiobook() {
        return edition(ENGLISH, AUDIOBOOK_FORMAT, null);
    }

    public SeriesBookJson isbn(final String isbn13) {
        return edition(ENGLISH, READ_FORMAT, isbn13);
    }

    private SeriesBookJson edition(final String language, final String format, final @Nullable String isbn13) {
        final ObjectNode edition = bookFields.putObject("default_physical_edition");
        edition.putObject("reading_format").put("format", format);
        edition.putObject(LANGUAGE).put(LANGUAGE, language);
        edition.put("isbn_13", isbn13);
        return this;
    }

    double position() {
        return volumePosition;
    }

    ObjectNode fields() {
        return bookFields;
    }
}
