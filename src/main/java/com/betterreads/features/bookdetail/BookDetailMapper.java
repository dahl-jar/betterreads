package com.betterreads.features.bookdetail;

import java.util.List;
import java.util.Objects;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookAward;
import com.betterreads.book.BookSubject;
import com.betterreads.booksource.SourceBook;
import com.betterreads.images.CoverImages;
import com.betterreads.pendingbook.PendingBook;
import com.betterreads.pendingbook.PendingBookMapper;
import org.springframework.stereotype.Component;

/** Builds the book detail response from a promoted book or a staging seed. */
@Component
class BookDetailMapper {

    private final PendingBookMapper pendingBookMapper;

    private final CoverImages coverImages;

    public BookDetailMapper(final PendingBookMapper pendingBookMapper, final CoverImages coverImages) {
        this.pendingBookMapper = pendingBookMapper;
        this.coverImages = coverImages;
    }

    /** Maps a promoted book, marked complete. */
    public BookDetailResponse fromBook(final Book book) {
        return BookDetailResponse.builder(book.getDedupKey(), true)
            .title(book.getTitle())
            .subtitle(book.getSubtitle())
            .authors(Author.sortedNames(book.getAuthors()))
            .description(book.getDescription())
            .coverUrl(coverImages.servedUrl(book))
            .firstPublishYear(book.getFirstPublishYear())
            .isbn(book.getIsbn())
            .pageCount(book.getPageCount())
            .language(book.getLanguage())
            .averageRating(book.getAverageRating())
            .ratingCount(book.getRatingCount())
            .seriesName(book.getSeriesName())
            .seriesPosition(book.getSeriesPosition())
            .series(book.getSeries())
            .subjects(book.getSubjects().stream().map(BookSubject::getSubject).toList())
            .awards(book.getAwards().stream().map(BookAward::getAward).toList())
            .build();
    }

    /** Maps a staging seed, marked incomplete. */
    public BookDetailResponse fromPending(final PendingBook row) {
        final SourceBook seed = pendingBookMapper.toSourceBook(row);
        return BookDetailResponse.builder(row.getDedupKey(), false)
            .title(Objects.requireNonNullElse(seed.title(), ""))
            .subtitle(seed.subtitle())
            .authors(Objects.requireNonNullElse(seed.authorNames(), List.<String>of()).stream().sorted().toList())
            .description(seed.description())
            .coverUrl(coverImages.servedUrl(row.getDedupKey(), seed.coverUrl()))
            .firstPublishYear(seed.publicationYear())
            .isbn(seed.isbn13())
            .pageCount(seed.pageCount())
            .language(seed.language())
            .averageRating(row.getAverageRating())
            .ratingCount(seed.ratingCount())
            .seriesName(seed.seriesName())
            .seriesPosition(seed.seriesPosition())
            .series(seed.series())
            .subjects(Objects.requireNonNullElse(seed.rawSubjects(), List.of()))
            .awards(Objects.requireNonNullElse(seed.awards(), List.of()))
            .build();
    }
}
