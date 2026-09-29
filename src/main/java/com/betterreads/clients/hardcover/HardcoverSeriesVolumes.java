package com.betterreads.clients.hardcover;

import java.util.List;
import java.util.Optional;

import com.betterreads.booksource.IssueRunSeries;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;

public final class HardcoverSeriesVolumes {

    private HardcoverSeriesVolumes() {
    }

    public static SourceBook.Builder withSeriesOf(final SourceBook.Builder builder, final HardcoverBookNode node) {
        return Optional.ofNullable(node.bookSeries())
            .flatMap(memberships -> volumeMembership(memberships, node.title()))
            .flatMap(membership -> Optional.ofNullable(membership.series())
                .map(series -> builder.seriesName(series.name()).seriesPosition(membership.position())))
            .orElse(builder);
    }

    private static Optional<HardcoverBookNode.SeriesMembership> volumeMembership(
        final List<HardcoverBookNode.SeriesMembership> all, final @Nullable String title) {
        final List<HardcoverBookNode.SeriesMembership> memberships = withoutOneBookSeries(all);
        if (memberships.isEmpty()) {
            return Optional.empty();
        }
        final HardcoverBookNode.SeriesMembership primary = memberships.stream()
            .filter(entry -> Boolean.TRUE.equals(entry.featured()))
            .findFirst()
            .orElse(memberships.get(0));
        if (!isIssueRun(primary, title)) {
            return Optional.of(primary).filter(HardcoverSeriesVolumes::isVolume);
        }
        return memberships.stream()
            .filter(entry -> isVolume(entry) && !isIssueRun(entry, title))
            .findFirst();
    }

    private static List<HardcoverBookNode.SeriesMembership> withoutOneBookSeries(
        final List<HardcoverBookNode.SeriesMembership> memberships) {
        final boolean hasLongerSeries = memberships.stream()
            .anyMatch(entry -> isVolume(entry) && !isOneBookSeries(entry));
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

    private static boolean isVolume(final HardcoverBookNode.SeriesMembership membership) {
        return membership.series() != null && membership.series().name() != null
            && HardcoverVolumeNumber.isVolume(membership.position());
    }
}
