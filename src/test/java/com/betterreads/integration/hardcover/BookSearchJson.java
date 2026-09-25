package com.betterreads.integration.hardcover;

import com.betterreads.integration.Fixtures;
import com.betterreads.integration.hardcover.dto.HardcoverDocument;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SuppressWarnings("PMD.TooManyMethods")
public final class BookSearchJson {

    private static final String TITLE = "title";

    private static final String CONTRIBUTIONS = "contributions";

    private final ObjectNode json = Fixtures.json("hardcover/book-search.json");

    private BookSearchJson() {
    }

    public static BookSearchJson bookSearch() {
        return new BookSearchJson();
    }

    public BookSearchJson withId(final String id) {
        pickedHit().put("id", id);
        return this;
    }

    public BookSearchJson withTitle(final String title) {
        pickedHit().put(TITLE, title);
        return this;
    }

    public BookSearchJson withoutTitle() {
        pickedHit().remove(TITLE);
        return this;
    }

    public BookSearchJson withAuthors(final String... authors) {
        final ArrayNode names = pickedHit().putArray("author_names");
        withoutCredits();
        for (final String author : authors) {
            names.add(author);
            withCredit(Credits.AUTHOR, author);
        }
        return this;
    }

    public BookSearchJson withCredit(final @Nullable String role, final String name) {
        Credits.add(pickedHit().withArray(CONTRIBUTIONS), role, name);
        return this;
    }

    public BookSearchJson withoutCredits() {
        pickedHit().putArray(CONTRIBUTIONS);
        return this;
    }

    public BookSearchJson withoutGenres() {
        pickedHit().remove("genres");
        return this;
    }

    public BookSearchJson withFeaturedSeries(final String name) {
        return withFeaturedSeries(name, 1.0);
    }

    public BookSearchJson withFeaturedSeries(final String name, final @Nullable Double position) {
        pickedHit().putObject("featured_series").put("position", position).putObject("series").put("name", name);
        return this;
    }

    public BookSearchJson withoutHits() {
        SearchHits.clear(json);
        return this;
    }

    public HardcoverDocument document() {
        return Fixtures.convert(pickedHit(), HardcoverDocument.class);
    }

    @Override
    public String toString() {
        return json.toString();
    }

    private ObjectNode pickedHit() {
        return SearchHits.picked(json);
    }
}
