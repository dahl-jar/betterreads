package com.betterreads.features.bookstaging;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceAuthorWorks;
import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceSeries;
import com.betterreads.booksource.SourceSeriesVolume;
import org.jspecify.annotations.Nullable;

final class DiscoverySamples {

    static final String WHEEL_OF_TIME = "The Wheel of Time";

    static final String GAPPED_WHEEL_OF_TIME = "The Wheel of Time, gapped";

    static final String SANDERSON = "Brandon Sanderson";

    static final String EYE = "The Eye of the World";

    static final String GREAT_HUNT = "The Great Hunt";

    static final String DRAGON_REBORN = "The Dragon Reborn";

    static final String CANONICAL_KEY = "OL1168083W";

    static final String SECOND_VOLUME_KEY = "OL2W";

    static final int CANONICAL_YEAR = 1949;

    static final int SECOND_POSITION = 2;

    private static final String AUTHOR = "Robert Jordan";

    private static final String UNKNOWN_VOLUME = "A Volume No Source Knows";

    private static final String MISTBORN = "Mistborn: The Final Empire";

    private static final String WAY_OF_KINGS = "The Way of Kings";

    private static final String CANONICAL_TITLE = "Nineteen Eighty-Four";

    private static final int FIRST_POSITION = 1;

    private static final int THIRD_POSITION = 3;

    private static final int LATER_EDITION_YEAR = 2003;

    private static final Map<String, String> WORK_KEYS_BY_TITLE = Map.of(
        EYE, "OL1W",
        GREAT_HUNT, SECOND_VOLUME_KEY,
        DRAGON_REBORN, "OL3W",
        MISTBORN, "OL10W",
        WAY_OF_KINGS, "OL11W",
        WHEEL_OF_TIME, "OL4W");

    private DiscoverySamples() {
    }

    static SourceSeries wheelOfTime() {
        return series(WHEEL_OF_TIME, GREAT_HUNT, null);
    }

    static SourceSeries gappedWheelOfTime() {
        return series(GAPPED_WHEEL_OF_TIME, UNKNOWN_VOLUME, null);
    }

    static SourceSeries titledWheelOfTime() {
        return series(WHEEL_OF_TIME, GREAT_HUNT, book(WHEEL_OF_TIME, AUTHOR).build());
    }

    static SourceAuthorWorks sandersonWorks() {
        return new SourceAuthorWorks(SANDERSON, List.of(
            book(MISTBORN, SANDERSON).build(), book(WAY_OF_KINGS, SANDERSON).build()));
    }

    static List<SourceBook> noisyStandaloneHits() {
        return List.of(
            standaloneHit("SparkNotes for 1984", "OLsparkW", LATER_EDITION_YEAR),
            standaloneHit("1984 (adaptation)", "OLadaptW", LATER_EDITION_YEAR),
            standaloneHit("Animal Farm / Nineteen Eighty-Four", "OLcomboW", LATER_EDITION_YEAR),
            standaloneHit(CANONICAL_TITLE, "OLreprintW", LATER_EDITION_YEAR),
            standaloneHit(CANONICAL_TITLE, CANONICAL_KEY, CANONICAL_YEAR));
    }

    static Optional<SourceBook> openLibraryHit(final String title) {
        return Optional.ofNullable(WORK_KEYS_BY_TITLE.get(title))
            .map(workKey -> SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .openLibraryWorkKey(workKey)
                .title(title)
                .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
                .build());
    }

    private static SourceSeries series(
        final String name, final String secondVolume, final @Nullable SourceBook titleBook) {
        return new SourceSeries(name, AUTHOR, List.of(
            volume(EYE, FIRST_POSITION),
            volume(secondVolume, SECOND_POSITION),
            volume(DRAGON_REBORN, THIRD_POSITION)), titleBook);
    }

    private static SourceSeriesVolume volume(final String title, final double position) {
        return new SourceSeriesVolume(position, book(title, AUTHOR)
            .seriesName(WHEEL_OF_TIME)
            .seriesPosition(position)
            .build());
    }

    private static SourceBook.Builder book(final String title, final String author) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .title(title)
            .authors(SourceAuthor.ofNames(List.of(author)));
    }

    private static SourceBook standaloneHit(final String title, final String key, final int year) {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey(key)
            .title(title)
            .publicationYear(year)
            .authors(SourceAuthor.ofNames(List.of("George Orwell")))
            .build();
    }
}
