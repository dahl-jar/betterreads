package com.betterreads.search.mapper;

import com.betterreads.catalog.dto.BookIndexView;
import com.betterreads.search.dto.BookSearchDocument;
import org.springframework.stereotype.Component;

/**
 * Maps a catalog {@link BookIndexView} to its search document.
 *
 * <p>The document id is the same key the detail endpoint resolves by, the first present source
 * identifier, so a search hit links to its detail page. Popularity scores rating volume and average
 * together so a widely loved book outranks an obscure one on a tie.
 */
@Component
public class BookSearchDocumentMapper {

    public BookSearchDocument toDocument(final BookIndexView book) {
        return BookSearchDocument.builder(book.dedupKey())
            .title(book.title())
            .subtitle(book.subtitle())
            .seriesName(book.seriesName())
            .seriesPosition(book.seriesPosition())
            .authors(book.authors())
            .subjects(book.subjects())
            .language(book.language())
            .coverUrl(book.servedCoverUrl())
            .publicationYear(book.firstPublishYear())
            .popularityScore(popularityScore(book))
            .build();
    }

    private static double popularityScore(final BookIndexView book) {
        final Integer ratingCount = book.ratingCount();
        if (ratingCount == null || ratingCount <= 0) {
            return 0.0;
        }
        final double average = book.averageRating() == null ? 0.0 : book.averageRating().doubleValue();
        return Math.log10(1 + ratingCount) * average;
    }
}
