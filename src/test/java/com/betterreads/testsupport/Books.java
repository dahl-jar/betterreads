package com.betterreads.testsupport;

import com.betterreads.book.Author;
import com.betterreads.book.Book;

import java.util.List;

public final class Books {

    public static final String DUNE_KEY = "OL893415W";

    public static final String DUNE_TITLE = "Dune";

    public static final String DUNE_AUTHOR = "Frank Herbert";

    public static final String DUNE_ISBN = "9780441013593";

    public static final String DUNE_QID = "Q190192";

    public static final String FRENCH_BLURB =
        "Ils m'appellent Vis Telimus. Ils croient que j'ai eu la chance d'être adopté par un "
        + "sénateur et envoyé à l'Académie pour rejoindre l'élite. Celle-ci exploite l'énergie "
        + "mentale des castes inférieures, leur Volonté, pour se doter de talents extraordinaires. "
        + "Ainsi la Hiérarchie a-t-elle conquis le monde.";

    private Books() {
    }

    public static Author author(final String name) {
        final Author author = new Author();
        author.setName(name);
        return author;
    }

    public static Book withId(final long bookId) {
        final Book book = new Book();
        book.setBookId(bookId);
        return book;
    }

    public static List<Long> idsOf(final List<Book> books) {
        return books.stream().map(Book::getBookId).toList();
    }
}
