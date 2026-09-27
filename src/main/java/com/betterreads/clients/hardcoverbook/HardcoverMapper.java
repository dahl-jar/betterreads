package com.betterreads.clients.hardcoverbook;

import java.util.List;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CatalogGenres;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverContributors;
import com.betterreads.clients.hardcover.HardcoverSeriesVolumes;
import com.betterreads.clients.hardcover.HardcoverVolumeNumber;
import com.betterreads.clients.hardcoverbook.HardcoverDocument.FeaturedSeries;
import com.betterreads.isbn.Isbn13;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class HardcoverMapper {

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
            .rawSubjects(document.genres() == null ? null
                : CatalogGenres.reduceToCanonical(document.genres()))
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

    private static @Nullable String firstIsbn13(final @Nullable List<String> isbns) {
        if (isbns == null) {
            return null;
        }
        return isbns.stream().filter(Isbn13::matches).findFirst().orElse(null);
    }

    private static @Nullable Integer seriesPosition(final @Nullable Double position) {
        return HardcoverVolumeNumber.fromPosition(position).orElse(null);
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
