package com.betterreads.clients.hardcover;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.betterreads.booksource.IssueRunSeries;
import com.betterreads.booksource.SeriesNumber;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;

public final class HardcoverSeriesVolumes {

    private HardcoverSeriesVolumes() {
    }

    public static SourceBook.Builder withSeriesOf(final SourceBook.Builder builder, final HardcoverBookNode node) {
        final List<HardcoverBookNode.SeriesMembership> memberships =
            withoutOneBookSeries(Objects.requireNonNullElse(node.bookSeries(), List.of()));
        return primaryMembership(memberships, node.featuredBookSeries(), node.title())
            .map(primary -> builder
                .seriesName(Objects.requireNonNull(primary.series()).name())
                .seriesPosition(SeriesNumber.of(primary.position()).orElseThrow()))
            .orElse(builder);
    }

    private static Optional<HardcoverBookNode.SeriesMembership> primaryMembership(
        final List<HardcoverBookNode.SeriesMembership> memberships,
        final HardcoverBookNode.@Nullable SeriesMembership pick,
        final @Nullable String title
    ) {
        if (memberships.isEmpty()) {
            return Optional.empty();
        }
        final HardcoverBookNode.SeriesMembership primary = pick != null && memberships.contains(pick)
            ? pick
            : memberships.stream()
                .filter(membership -> Boolean.TRUE.equals(membership.featured()))
                .findFirst()
                .orElse(memberships.getFirst());
        if (!isIssueRun(primary, title)) {
            return Optional.of(primary).filter(HardcoverSeriesVolumes::isNumbered);
        }
        return memberships.stream()
            .filter(membership -> isNumbered(membership) && !isIssueRun(membership, title))
            .findFirst();
    }

    private static List<HardcoverBookNode.SeriesMembership> withoutOneBookSeries(
        final List<HardcoverBookNode.SeriesMembership> memberships) {
        final boolean hasLongerSeries = memberships.stream()
            .anyMatch(entry -> isNumbered(entry) && !isOneBookSeries(entry));
        return hasLongerSeries
            ? memberships.stream().filter(entry -> !isOneBookSeries(entry)).toList()
            : memberships;
    }

    public static boolean isIssueRun(final @Nullable String seriesName, final @Nullable String title) {
        return seriesName != null && title != null && IssueRunSeries.matches(seriesName, title);
    }

    private static boolean isIssueRun(
        final HardcoverBookNode.SeriesMembership membership, final @Nullable String title) {
        return membership.series() != null && isIssueRun(membership.series().name(), title);
    }

    private static boolean isOneBookSeries(final HardcoverBookNode.SeriesMembership membership) {
        return membership.series() != null
            && Integer.valueOf(1).equals(membership.series().primaryBooksCount());
    }

    private static boolean isNumbered(final HardcoverBookNode.SeriesMembership membership) {
        return membership.series() != null && membership.series().name() != null
            && SeriesNumber.of(membership.position()).isPresent();
    }
}
