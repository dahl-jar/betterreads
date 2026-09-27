package com.betterreads.testsupport;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;

import java.util.Set;

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

    public static final int PUBLISH_YEAR = 1965;

    private static final String DESCRIPTION =
        "A young heir is betrayed into the deep desert and rises among its people to take back his house.";

    private Books() {
    }

    public static Author author(final String name) {
        final Author author = new Author();
        author.setName(name);
        return author;
    }

    public static Book book(final String key, final String title) {
        final Book book = new Book();
        book.setDedupKey(key);
        book.setTitle(title);
        return book;
    }

    public static Book seedBook(final BookRepository books, final String key, final String title) {
        return books.save(book(key, title));
    }

    public static Book promoted(final String key, final String title, final Author author) {
        final Book book = book(key, title);
        book.setDescription(DESCRIPTION);
        book.setCoverUrl("https://covers.example/" + key + ".jpg");
        book.setFirstPublishYear(PUBLISH_YEAR);
        book.setAuthors(Set.of(author));
        return book;
    }
}
