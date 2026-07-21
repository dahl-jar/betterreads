package com.betterreads.catalog.service.read;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.common.exception.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves a book's dedup key to its id. */
@Service
public class BookIdLookup {

    private final BookRepository books;

    public BookIdLookup(final BookRepository books) {
        this.books = books;
    }

    /** Returns the id of the book with the given dedup key, or throws when no book matches. */
    @Transactional(readOnly = true)
    public long requireBookId(final String dedupKey) {
        return books.findByDedupKey(dedupKey)
            .map(Book::getBookId)
            .orElseThrow(() -> new ResourceNotFoundException("No book with key " + dedupKey));
    }
}
