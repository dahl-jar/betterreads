package com.betterreads.search.mapper;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.entity.BookSubject;
import com.betterreads.search.dto.BookSearchDocument;
import org.springframework.stereotype.Component;

/**
 * Maps a catalog {@link Book} to its search document.
 *
 * <p>The document id is the same key the detail endpoint resolves by, the first present source
 * identifier, so a search hit links straight to its detail page. Popularity scores rating volume and
 * average together so a widely loved book outranks an obscure one on a tie.
 */
@Component
public class BookSearchDocumentMapper {

    /** Builds the search document for the given book. */
    public BookSearchDocument toDocument(final Book book) {
        return new BookSearchDocument(
            book.getDedupKey(),
            book.getTitle(),
            book.getSubtitle(),
            book.getAuthors().stream().map(Author::getName).sorted().toList(),
            book.getSubjects().stream().map(BookSubject::getSubject).toList(),
            book.getLanguage(),
            book.getFirstPublishYear(),
            popularityScore(book));
    }

    private static double popularityScore(final Book book) {
        final Integer ratingCount = book.getRatingCount();
        if (ratingCount == null || ratingCount <= 0) {
            return 0.0;
        }
        final double average = book.getAverageRating() == null ? 0.0 : book.getAverageRating().doubleValue();
        return Math.log10(1 + ratingCount) * average;
    }
}
