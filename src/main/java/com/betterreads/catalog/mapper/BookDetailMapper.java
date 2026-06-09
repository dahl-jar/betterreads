package com.betterreads.catalog.mapper;

import java.util.List;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.entity.BookAward;
import com.betterreads.catalog.entity.BookSubject;
import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.image.CoverImages;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link BookDetailResponse} from a promoted {@link Book} or a staging {@link PendingBook}.
 *
 * <p>The pending seed stores authors, subjects, and awards as delimited text, so it routes through
 * {@link PendingBookMapper#toSourceBook} to split them once rather than repeat the parsing here.
 */
@Component
public class BookDetailMapper {

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
            .authors(book.getAuthors().stream().map(Author::getName).sorted().toList())
            .description(book.getDescription())
            .coverUrl(coverImages.servedUrl(book.getDedupKey(), book.getCoverUrl()))
            .firstPublishYear(book.getFirstPublishYear())
            .isbn(book.getIsbn())
            .pageCount(book.getPageCount())
            .language(book.getLanguage())
            .averageRating(book.getAverageRating())
            .ratingCount(book.getRatingCount())
            .seriesName(book.getSeriesName())
            .seriesPosition(book.getSeriesPosition())
            .subjects(book.getSubjects().stream().map(BookSubject::getSubject).toList())
            .awards(book.getAwards().stream().map(BookAward::getAward).toList())
            .build();
    }

    /** Maps a staging seed, marked incomplete, with enrichment-only fields left null. */
    public BookDetailResponse fromPending(final PendingBook row) {
        final SourceBook seed = pendingBookMapper.toSourceBook(row);
        return BookDetailResponse.builder(row.getDedupKey(), false)
            .title(orEmptyTitle(seed.title()))
            .subtitle(seed.subtitle())
            .authors(names(seed.authors()))
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
            .subjects(orEmpty(seed.rawSubjects()))
            .awards(orEmpty(seed.awards()))
            .build();
    }

    private static String orEmptyTitle(final @Nullable String title) {
        return title == null ? "" : title;
    }

    private static List<String> names(final @Nullable List<SourceAuthor> authors) {
        return authors == null ? List.of() : authors.stream().map(SourceAuthor::name).sorted().toList();
    }

    private static List<String> orEmpty(final @Nullable List<String> values) {
        return values == null ? List.of() : values;
    }
}
