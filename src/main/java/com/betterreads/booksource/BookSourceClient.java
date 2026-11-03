package com.betterreads.booksource;

import java.util.Optional;

public interface BookSourceClient {

    BookFieldSource source();

    Optional<SourceBook> fetchByIsbn(String isbn);

    Optional<SourceBook> fetchByTitleAuthor(String title, String firstAuthor);
}
