package com.betterreads.integration.hardcover.mapper;

import java.util.List;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.quality.CatalogGenres;
import com.betterreads.catalog.service.source.model.SourceAuthor;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.common.util.Isbn13;
import com.betterreads.integration.hardcover.dto.HardcoverBookNode;
import com.betterreads.integration.hardcover.dto.HardcoverDocument;
import com.betterreads.integration.hardcover.dto.HardcoverDocument.FeaturedSeries;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class HardcoverMapper {

    static final int MAX_GENRES = 25;

    private static final int FIRST_VOLUME = 1;

    public @Nullable SourceBook toSourceBook(final @Nullable HardcoverBookNode node) {
        return HardcoverBookNodeMapper.toSourceBookWithSeries(node).orElse(null);
    }

    public @Nullable SourceBook toSourceBook(final @Nullable HardcoverDocument document) {
        if (document == null || document.title() == null) {
            return null;
        }
        final FeaturedSeries series = document.featuredSeries();
        final Integer position = series == null ? null : seriesPosition(series.position());
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(firstIsbn13(document.isbns()))
            .hardcoverId(document.id())
            .title(document.title())
            .description(document.description())
            .publicationYear(document.releaseYear())
            .pageCount(document.pages())
            .coverUrl(coverUrl(document))
            .authors(authors(document))
            .rawSubjects(document.genres() == null ? null : cleanGenres(document.genres()))
            .averageRating(document.rating())
            .ratingCount(document.ratingsCount())
            .seriesName(position == null ? null : seriesName(series))
            .seriesPosition(position)
            .build();
    }

    public boolean hasIssueRunSeries(final HardcoverDocument document) {
        final FeaturedSeries series = document.featuredSeries();
        return series != null && series.series() != null
            && HardcoverSeriesVolumes.isIssueRun(series.series().name(), document.title());
    }

    public SourceBook withSeriesOf(final SourceBook book, final HardcoverBookNode node) {
        final SourceBook.Builder withoutSeries = book.toBuilder().seriesName(null).seriesPosition(null);
        return HardcoverSeriesVolumes.withSeriesOf(withoutSeries, node).build();
    }

    static @Nullable String firstIsbn13(final @Nullable List<String> isbns) {
        if (isbns == null) {
            return null;
        }
        return isbns.stream().filter(Isbn13::matches).findFirst().orElse(null);
    }

    static List<String> cleanGenres(final @Nullable List<String> genres) {
        return CatalogGenres.reduceToCanonical(genres, MAX_GENRES);
    }

    static @Nullable Integer seriesPosition(final @Nullable Double position) {
        return VolumeNumber.fromPosition(position)
            .filter(volume -> volume >= FIRST_VOLUME)
            .orElse(null);
    }

    private static @Nullable List<SourceAuthor> authors(final HardcoverDocument document) {
        final List<HardcoverBookNode.Contribution> contributions = document.contributions();
        return contributions == null || contributions.isEmpty()
            ? SourceAuthor.ofNames(document.authorNames())
            : HardcoverContributors.authors(contributions);
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
