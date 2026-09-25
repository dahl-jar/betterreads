package com.betterreads.integration.hardcover;

import com.betterreads.integration.Fixtures;
import com.betterreads.integration.hardcover.dto.HardcoverBookNode;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class BookByIdJson {

    private static final String BOOK_SERIES = "book_series";

    private final ObjectNode json = Fixtures.json("hardcover/book-by-id.json");

    private BookByIdJson() {
    }

    public static BookByIdJson bookById() {
        return new BookByIdJson();
    }

    public BookByIdJson withTitle(final String title) {
        book().put("title", title);
        return this;
    }

    public BookByIdJson withCredit(final @Nullable String role, final String name) {
        Credits.add(book().withArray("contributions"), role, name);
        return this;
    }

    public BookByIdJson withOnlyFeaturedSeries() {
        series().remove(1);
        return this;
    }

    public BookByIdJson withoutSeries() {
        book().putArray(BOOK_SERIES);
        return this;
    }

    public BookByIdJson withSeries(final String name, final @Nullable Integer position, final boolean featured) {
        series().addObject().put("position", position).put("featured", featured)
            .putObject("series").put("name", name);
        return this;
    }

    public BookByIdJson withoutBooks() {
        ((ObjectNode) json.path("data")).putArray("books");
        return this;
    }

    public HardcoverBookNode node() {
        return Fixtures.convert(book(), HardcoverBookNode.class);
    }

    @Override
    public String toString() {
        return json.toString();
    }

    private ObjectNode book() {
        return (ObjectNode) json.at("/data/books/0");
    }

    private ArrayNode series() {
        return book().withArray(BOOK_SERIES);
    }
}
