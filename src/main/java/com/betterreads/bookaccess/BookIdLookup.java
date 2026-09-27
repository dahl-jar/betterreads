package com.betterreads.bookaccess;

import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.errors.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves a book's dedup key to its id. */
@Service
public class BookIdLookup {

    private final BookRepository books;

    public BookIdLookup(final BookRepository books) {
        this.books = books;
    }

    @Transactional(readOnly = true)
    public long requireBookId(final String dedupKey) {
        return books.findByDedupKey(dedupKey)
            .map(Book::getBookId)
            .orElseThrow(() -> new ResourceNotFoundException("No book with key " + dedupKey));
    }
}
