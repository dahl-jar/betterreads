package com.betterreads.features.search;

import com.betterreads.bookindex.BookIndexView;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps a catalog book to its search document.
 *
 * <p>The document id is the key the detail endpoint resolves by, so a search hit links to its
 * detail page. Popularity weighs rating count and average together so a widely loved book outranks
 * an obscure one on a tie.
 */
@Component
class BookSearchDocumentMapper {

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
        final double average = Optional.ofNullable(book.averageRating()).map(BigDecimal::doubleValue).orElse(0.0);
        return Math.log10(1 + ratingCount) * average;
    }
}
