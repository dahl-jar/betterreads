package com.betterreads.book;

import java.util.List;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;

final class BookSeriesSamples {

    static final String HARDCOVER_ID = "hc-1";

    static final SeriesEntry STORMLIGHT = new SeriesEntry("The Stormlight Archive", 2);

    static final SeriesEntry COSMERE = new SeriesEntry("The Cosmere", 12);

    static final String AUTHOR = "Author";

    static final String OTHER_AUTHOR = "Other Author";

    private BookSeriesSamples() {
    }

    static SourceBook wordsOfRadiance(final List<SeriesEntry> series) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .hardcoverId(HARDCOVER_ID)
            .title("Words of Radiance")
            .authors(SourceAuthor.ofNames(List.of(AUTHOR, OTHER_AUTHOR)))
            .series(series)
            .build();
    }
}
