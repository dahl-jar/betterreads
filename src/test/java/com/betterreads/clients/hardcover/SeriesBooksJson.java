package com.betterreads.clients.hardcover;

import com.betterreads.clients.Fixtures;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class SeriesBooksJson {

    private static final String BOOK_SERIES = "book_series";

    private static final String POSITION = "position";

    private static final String BOOK = "book";

    private static final String TITLE = "title";

    private static final String NAME = "name";

    private static final int GRAPHIC_NOVEL_CATEGORY = 4;

    private final ObjectNode json = Fixtures.parse("""
        {"data": {"series": [{
          "id": 1097, "name": "The Wheel of Time", "primary_books_count": 3,
          "book_series": [
            {"position": 0, "book": {"title": "New Spring", "users_count": 500,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Eye of the World: Audiobook", "users_count": 30,
              "default_physical_edition": {"reading_format": {"format": "Listened"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Wheel of Time: Boxed Set #1", "users_count": 40,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Eye of the World: The Deluxe Edition",
              "users_count": 99000,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Eye of the World, Part 1", "users_count": 80000,
              "is_partial_book": true,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Wheel of Time Trilogy", "users_count": 70000,
              "book_category_id": 8,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 1, "book": {"title": "The Eye of the World", "users_count": 9000,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 2, "book": {"title": "Oko Świata", "users_count": 100,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "Polish"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 2, "book": {"title": "The Great Hunt", "users_count": 7000,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 3, "book": {"title": "Smok Odrodzony", "users_count": 50,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "Polish"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}},
            {"position": 4, "book": {"title": "A Crown of Swords", "users_count": 6000,
              "default_physical_edition": {"reading_format": {"format": "Read"},
                "language": {"language": "English"}},
              "contributions": [{"author": {"name": "Robert Jordan"}}]}}
          ]
        }]}}
        """);

    private final ObjectNode template = ((ObjectNode) series().path(BOOK_SERIES).get(0)).deepCopy();

    private SeriesBooksJson() {
    }

    public static SeriesBooksJson seriesBooks() {
        return new SeriesBooksJson();
    }

    public SeriesBooksJson withName(final String name) {
        series().put(NAME, name);
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

    public SeriesBooksJson withMembership(final String name, final int position, final boolean featured) {
        final ObjectNode lastVolume = (ObjectNode) volumes().get(volumes().size() - 1);
        lastVolume.withObject(BOOK).withArray(BOOK_SERIES).addObject()
            .put(POSITION, position).put("featured", featured)
            .putObject("series").put(NAME, name);
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
