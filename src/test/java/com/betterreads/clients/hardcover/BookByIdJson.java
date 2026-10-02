package com.betterreads.clients.hardcover;

import com.betterreads.clients.Fixtures;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// PMD.TooManyMethods: one builder method per book variation the tests need.
@SuppressWarnings("PMD.TooManyMethods")
public final class BookByIdJson {

    public static final String STORMLIGHT = "The Stormlight Archive";

    public static final String COSMERE = "The Cosmere";

    public static final int STORMLIGHT_VOLUME = 2;

    public static final int COSMERE_VOLUME = 12;

    public static final int STORMLIGHT_BOOKS = 5;

    public static final int COSMERE_BOOKS = 35;

    private static final String BOOK_SERIES = "book_series";

    private final ObjectNode json = Fixtures.parse("""
        {"data": {"books": [{
          "id": 2235304,
          "title": "Absolute Batman, Vol. 2: Abomination",
          "description": "Batman faces the Abomination in the ruins of Gotham.",
          "rating": 4.09, "ratings_count": 16, "users_count": 40, "release_year": 2026,
          "default_physical_edition": {"language": {"language": "English"}},
          "contributions": [{"contribution": "Author", "author": {"name": "Scott Snyder"}}],
          "book_series": [
            {"position": 7, "featured": true, "series": {"name": "Absolute Batman (2024) (Single Issues)"}},
            {"position": 2, "featured": false, "series": {"name": "Absolute Batman (2024)"}}
          ]
        }]}}
        """);

    private BookByIdJson() {
    }

    public static BookByIdJson bookById() {
        return new BookByIdJson();
    }

    public static BookByIdJson wordsOfRadiance() {
        return bookById().withTitle("Words of Radiance").withoutSeries()
            .withSeries(COSMERE, COSMERE_VOLUME, false, COSMERE_BOOKS)
            .withSeries(STORMLIGHT, STORMLIGHT_VOLUME, true, STORMLIGHT_BOOKS);
    }

    public BookByIdJson withTitle(final String title) {
        book().put("title", title);
        return this;
    }

    public BookByIdJson withCredit(final @Nullable String role, final String name) {
        HardcoverCredits.add(book().withArray("contributions"), role, name);
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
        return withSeries(name, position, featured, null);
    }

    public BookByIdJson withSeries(
        final String name,
        final @Nullable Integer position,
        final boolean featured,
        final @Nullable Integer primaryBooksCount
    ) {
        series().addObject().put("position", position).put("featured", featured)
            .putObject("series").put("name", name).put("primary_books_count", primaryBooksCount);
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
