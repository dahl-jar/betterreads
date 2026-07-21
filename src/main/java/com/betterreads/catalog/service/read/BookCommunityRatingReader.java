package com.betterreads.catalog.service.read;

import java.util.Optional;

import com.betterreads.catalog.dto.BookCommunityRating;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads a book's stored community rating. */
@Service
public class BookCommunityRatingReader {

    private final BookRepository books;

    public BookCommunityRatingReader(final BookRepository books) {
        this.books = books;
    }

    /** Returns the community rating for the book with the given key, or empty when none matches. */
    @Transactional(readOnly = true)
    public Optional<BookCommunityRating> byKey(final String dedupKey) {
        return books.findByDedupKey(dedupKey).map(BookCommunityRatingReader::toRating);
    }

    private static BookCommunityRating toRating(final Book book) {
        return new BookCommunityRating(
            book.getBookId(), book.getCommunityAverage(), book.getCommunityCount());
    }
}
