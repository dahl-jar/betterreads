package com.betterreads.integration.hardcover.mapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.CatalogGenres;
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
        return new SourceBook(
            BookFieldSource.HARDCOVER,
            firstIsbn13(document.isbns()),
            null,
            null,
            null,
            null,
            document.id(),
            document.title(),
            null,
            document.description(),
            document.releaseYear(),
            null,
            document.pages(),
            null,
            coverUrl(document),
            document.authorNames(),
            document.genres() == null ? null : cleanGenres(document.genres()),
            null,
            document.rating(),
            document.ratingsCount(),
            seriesName(series),
            series == null ? null : seriesPosition(series.position()));
    }

    static @Nullable String firstIsbn13(final @Nullable List<String> isbns) {
        if (isbns == null) {
            return null;
        }
        return isbns.stream().filter(isbn -> ISBN_13.matcher(isbn).matches()).findFirst().orElse(null);
    }

    static List<String> cleanGenres(final @Nullable List<String> genres) {
        if (genres == null) {
            return List.of();
        }
        final Set<String> canonical = new LinkedHashSet<>();
        for (final String genre : genres) {
            canonical.addAll(CatalogGenres.extractGenres(genre));
            if (canonical.size() >= MAX_GENRES) {
                break;
            }
        }
        return new ArrayList<>(canonical);
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
