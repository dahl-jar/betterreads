package com.betterreads.integration.hardcover.mapper;

import java.util.List;
import java.util.regex.Pattern;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.CatalogGenres;
import com.betterreads.catalog.service.SourceAuthor;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.integration.hardcover.dto.HardcoverDocument;
import com.betterreads.integration.hardcover.dto.HardcoverDocument.FeaturedSeries;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Maps a Hardcover search document into the catalog's {@link SourceBook}.
 *
 * <p>The genre list is reduced to canonical shelf genres via {@link CatalogGenres}, and the ISBN-13
 * is filtered out of the bulk ISBN array, which interleaves ISBN-10 and ISBN-13.
 */
@Component
public class HardcoverMapper {

    static final int MAX_GENRES = 25;

    private static final Pattern ISBN_13 = Pattern.compile("97[89]\\d{10}");

    /** Returns the {@link SourceBook} for a Hardcover document, or null if it has no title. */
    public @Nullable SourceBook toSourceBook(final @Nullable HardcoverDocument document) {
        if (document == null || document.title() == null) {
            return null;
        }
        final FeaturedSeries series = document.featuredSeries();
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(firstIsbn13(document.isbns()))
            .hardcoverId(document.id())
            .title(document.title())
            .description(document.description())
            .publicationYear(document.releaseYear())
            .pageCount(document.pages())
            .coverUrl(coverUrl(document))
            .authors(SourceAuthor.ofNames(document.authorNames()))
            .rawSubjects(document.genres() == null ? null : cleanGenres(document.genres()))
            .averageRating(document.rating())
            .ratingCount(document.ratingsCount())
            .seriesName(seriesName(series))
            .seriesPosition(series == null ? null : seriesPosition(series.position()))
            .build();
    }

    static @Nullable String firstIsbn13(final @Nullable List<String> isbns) {
        if (isbns == null) {
            return null;
        }
        return isbns.stream().filter(isbn -> ISBN_13.matcher(isbn).matches()).findFirst().orElse(null);
    }

    static List<String> cleanGenres(final @Nullable List<String> genres) {
        return CatalogGenres.reduceToCanonical(genres, MAX_GENRES);
    }

    static @Nullable Integer seriesPosition(final @Nullable Double position) {
        return position == null ? null : (int) Math.round(position);
    }

    private static @Nullable String coverUrl(final HardcoverDocument document) {
        return document.image() == null ? null : document.image().url();
    }

    private static @Nullable String seriesName(final @Nullable FeaturedSeries series) {
        if (series == null || series.series() == null) {
            return null;
        }
        return series.series().name();
    }
}
