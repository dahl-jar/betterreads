package com.betterreads.features.bookstaging;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceBooks;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.testsupport.Books;

import java.util.List;

final class DuneBooks {

    static final String ISBN = Books.DUNE_ISBN;

    static final String OL_KEY = Books.DUNE_KEY;

    static final String TITLE = Books.DUNE_TITLE;

    static final String AUTHOR = Books.DUNE_AUTHOR;

    static final String GENRE = "science fiction";

    static final double SERIES_POSITION = 1;

    static final String SECOND_EDITION_ISBN = "9780345298591";

    static final String SEQUEL_ISBN = "9780441172696";

    static final String SEQUEL_TITLE = "Dune Messiah";

    private static final int YEAR = 1965;

    private static final double RATING = 4.25;

    private DuneBooks() {
    }

    static PendingBook pendingRow(final String dedupKey, final String title) {
        final PendingBook row = new PendingBook();
        row.setDedupKey(dedupKey);
        row.setIsbn13(dedupKey);
        row.setTitle(title);
        return row;
    }

    static SourceBook openLibraryDune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build();
    }

    static SourceBook openLibraryCompleteDune() {
        return SourceBooks.dune().toBuilder()
            .openLibraryWorkKey(null)
            .build();
    }

    static SourceBook secondEditionDune() {
        return SourceBooks.dune().toBuilder()
            .isbn13(SECOND_EDITION_ISBN)
            .openLibraryWorkKey(null)
            .coverUrl("https://covers.example/dune-second-edition.jpg")
            .build();
    }

    static SourceBook collidingSecondEdition() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(SECOND_EDITION_ISBN)
            .openLibraryWorkKey(OL_KEY)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build();
    }

    static SourceBook duneMessiah() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(SEQUEL_ISBN)
            .title(SEQUEL_TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .coverUrl("https://covers.example/dune-messiah.jpg")
            .description("Twelve years after his victory, Paul Atreides rules as emperor of the known universe.")
            .publicationYear(YEAR)
            .rawSubjects(List.of(GENRE))
            .build();
    }

    static SourceBook hardcoverDune() {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .title(TITLE)
            .seriesName(TITLE)
            .seriesPosition(SERIES_POSITION)
            .averageRating(RATING)
            .build();
    }

    static SourceBook wikidataDuneChronicles() {
        return SourceBook.builder(BookFieldSource.WIKIDATA)
            .isbn13(ISBN)
            .title(TITLE)
            .seriesName("Dune Chronicles")
            .seriesPosition(SERIES_POSITION)
            .build();
    }

    static SourceBook completeDune() {
        return duneBy(AUTHOR);
    }

    static SourceBook duneBy(final String... authorNames) {
        return SourceBooks.dune().toBuilder()
            .authors(SourceAuthor.ofNames(List.of(authorNames)))
            .rawSubjects(List.of(GENRE))
            .build();
    }

    static SourceBook sparseDune() {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .openLibraryWorkKey(OL_KEY)
            .title(TITLE)
            .build();
    }
}
