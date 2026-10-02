package com.betterreads.clients.hardcover;

import java.util.Arrays;

import com.betterreads.clients.Fixtures;
import com.betterreads.clients.hardcoverbook.HardcoverDocument;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// PMD.TooManyMethods: one builder method per search-hit variation the tests need.
@SuppressWarnings("PMD.TooManyMethods")
public final class BookSearchJson {

    private static final String TITLE = "title";

    private static final String CONTRIBUTIONS = "contributions";

    private static final String FEATURED_SERIES = "featured_series";

    private static final String POSITION = "position";

    private static final String READ_COUNT = "users_read_count";

    private static final String AUTHOR_NAMES = "author_names";

    private static final String ISBNS = "isbns";

    private final ObjectNode json = Fixtures.parse("""
        {"data": {"search": {"results": {"hits": [
          {"document": {"id": "1", "title": "The Hobbit", "rating": 5.0, "ratings_count": 1,
            "users_read_count": 1, "author_names": []}},
          {"document": {"id": "9999", "title": "The Hobbit, or There and Back Again", "release_year": 1937,
            "description": "Bilbo Baggins is swept into a quest for a dragon's hoard.", "pages": 310,
            "rating": 4.31, "ratings_count": 6394, "users_read_count": 8616,
            "author_names": ["J.R.R. Tolkien", "Alan Lee"],
            "isbns": ["9788578276300"],
            "genres": ["Fantasy", "Classics", "Fiction"],
            "image": {"url": "https://covers.example.test/hobbit.jpg"},
            "featured_series": {"position": 0.0, "series": {"name": "The Lord of the Rings"}},
            "contributions": [
              {"contribution": "Author", "author": {"name": "J.R.R. Tolkien"}},
              {"contribution": "Illustrator", "author": {"name": "Alan Lee"}}
            ]}}
        ]}}}}
        """);

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
        final ArrayNode names = pickedHit().putArray(AUTHOR_NAMES);
        withoutCredits();
        for (final String author : authors) {
            names.add(author);
            withCredit(HardcoverCredits.AUTHOR, author);
        }
        return this;
    }

    public BookSearchJson withCredit(final @Nullable String role, final String name) {
        HardcoverCredits.add(pickedHit().withArray(CONTRIBUTIONS), role, name);
        return this;
    }

    public BookSearchJson withoutCredits() {
        pickedHit().putArray(CONTRIBUTIONS);
        return this;
    }

    public BookSearchJson withIsbns(final String... isbns) {
        final ArrayNode values = pickedHit().putArray(ISBNS);
        Arrays.stream(isbns).forEach(values::add);
        return this;
    }

    public BookSearchJson withoutIsbns() {
        pickedHit().remove(ISBNS);
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
        pickedHit().putObject(FEATURED_SERIES).put(POSITION, position).putObject("series").put("name", name);
        return this;
    }

    public BookSearchJson withSeriesNames(final String... names) {
        final ArrayNode values = pickedHit().putArray("series_names");
        Arrays.stream(names).forEach(values::add);
        return this;
    }

    public BookSearchJson withUnnamedFeaturedSeries() {
        pickedHit().putObject(FEATURED_SERIES).put(POSITION, 1.0);
        return this;
    }

    public BookSearchJson withReadCount(final int reads) {
        pickedHit().put(READ_COUNT, reads);
        return this;
    }

    public BookSearchJson withoutReadCount() {
        pickedHit().remove(READ_COUNT);
        return this;
    }

    public BookSearchJson withoutAuthorNames() {
        pickedHit().remove(AUTHOR_NAMES);
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
        return HardcoverSearchHits.picked(json);
    }
}
