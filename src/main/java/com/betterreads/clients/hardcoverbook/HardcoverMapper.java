package com.betterreads.clients.hardcoverbook;

import java.util.List;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CatalogGenres;
import com.betterreads.booksource.SeriesNumber;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverContributors;
import com.betterreads.clients.hardcover.HardcoverSeriesVolumes;
import com.betterreads.clients.hardcoverbook.HardcoverDocument.FeaturedSeries;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class HardcoverMapper {

    public @Nullable SourceBook toSourceBook(final @Nullable HardcoverDocument document) {
        if (document == null || document.title() == null) {
            return null;
        }
        final FeaturedSeries series = document.storySeries();
        final Double position = series == null ? null : SeriesNumber.of(series.position()).orElse(null);
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(document.firstIsbn13())
            .hardcoverId(document.id())
            .title(document.title())
            .description(document.description())
            .publicationYear(document.releaseYear())
            .pageCount(document.pages())
            .coverUrl(coverUrl(document))
            .authors(authors(document))
            .rawSubjects(document.genres() == null ? null
                : CatalogGenres.reduceToCanonical(document.genres()))
            .averageRating(document.rating())
            .ratingCount(document.ratingsCount())
            .seriesName(position == null ? null : seriesName(series))
            .seriesPosition(position)
            .build();
    }

    public boolean hasIssueRunSeries(final HardcoverDocument document) {
        return HardcoverSeriesVolumes.isIssueRun(seriesName(document.storySeries()), document.title());
    }

    public SourceBook withSeriesOf(final SourceBook book, final HardcoverBookNode node) {
        final SourceBook.Builder withoutSeries = book.toBuilder().seriesName(null).seriesPosition(null).series(null);
        return HardcoverSeriesVolumes.withSeriesOf(withoutSeries, node).build();
    }

    private static @Nullable List<SourceAuthor> authors(final HardcoverDocument document) {
        final List<HardcoverBookNode.Contribution> contributions = document.contributions();
        return contributions == null || contributions.isEmpty()
            ? SourceAuthor.ofNames(document.authorNames())
            : HardcoverContributors.credits(contributions);
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
