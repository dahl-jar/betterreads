package com.betterreads.clients.hardcover;

import java.util.Arrays;
import java.util.function.Consumer;

import com.betterreads.clients.Fixtures;
import com.betterreads.clients.hardcoverbook.HardcoverDocument;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class BookSearchJson {

    public static final String STUB_TITLE = "The Hobbit";

    public static final String HOBBIT_ID = "9999";

    public static final String HOBBIT_TITLE = "The Hobbit, or There and Back Again";

    public static final String HOBBIT_DESCRIPTION = "Bilbo Baggins is swept into a quest for a dragon's hoard.";

    public static final String HOBBIT_COVER = "https://covers.example.test/hobbit.jpg";

    public static final String HOBBIT_ISBN = "9788578276300";

    public static final int HOBBIT_YEAR = 1937;

    public static final int HOBBIT_PAGES = 310;

    public static final double HOBBIT_RATING = 4.31;

    public static final int HOBBIT_RATING_COUNT = 6394;

    public static final String TOLKIEN = "J.R.R. Tolkien";

    public static final String ALAN_LEE = "Alan Lee";

    public static final String LORD_OF_THE_RINGS = "The Lord of the Rings";

    static final String TITLE = "title";

    static final String AUTHOR_NAMES = "author_names";

    static final String ISBNS = "isbns";

    private static final String CONTRIBUTIONS = "contributions";

    private final ObjectNode json = Fixtures.parse("""
        {"data": {"search": {"results": {"hits": [
          {"document": {"id": "1", "rating": 5.0, "ratings_count": 1, "users_read_count": 1, "author_names": []}},
          {"document": {"users_read_count": 8616, "genres": ["Fantasy", "Classics", "Fiction"],
            "compilation": false, "book_category_id": 1}}
        ]}}}}
        """);

    private BookSearchJson() {
        ((ObjectNode) json.at("/data/search/results/hits/0/document")).put(TITLE, STUB_TITLE);
        pickedHit().put("id", HOBBIT_ID)
            .put(TITLE, HOBBIT_TITLE)
            .put("release_year", HOBBIT_YEAR)
            .put("description", HOBBIT_DESCRIPTION)
            .put("pages", HOBBIT_PAGES)
            .put("rating", HOBBIT_RATING)
            .put("ratings_count", HOBBIT_RATING_COUNT);
        pickedHit().putObject("image").put("url", HOBBIT_COVER);
        with(SearchHitFields.featuredSeries(LORD_OF_THE_RINGS, 0.0));
        withIsbns(HOBBIT_ISBN);
        pickedHit().putArray(AUTHOR_NAMES).add(TOLKIEN).add(ALAN_LEE);
        withCredit(HardcoverCredits.AUTHOR, TOLKIEN);
        withCredit(HardcoverCredits.ILLUSTRATOR, ALAN_LEE);
    }

    public static BookSearchJson bookSearch() {
        return new BookSearchJson();
    }

    public BookSearchJson with(final Consumer<ObjectNode> change) {
        change.accept(pickedHit());
        return this;
    }

    public BookSearchJson withId(final String id) {
        pickedHit().put("id", id);
        return this;
    }

    public BookSearchJson withTitle(final String title) {
        pickedHit().put(TITLE, title);
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
