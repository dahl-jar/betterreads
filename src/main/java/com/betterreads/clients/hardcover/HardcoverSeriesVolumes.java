package com.betterreads.clients.hardcover;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.booksource.IssueRunSeries;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;

public final class HardcoverSeriesVolumes {

    private static final Comparator<HardcoverBookNode.SeriesMembership> BY_BOOK_COUNT =
        Comparator.comparingInt(membership -> Optional.ofNullable(membership.series())
            .map(HardcoverBookNode.Series::primaryBooksCount)
            .orElse(0));

    private HardcoverSeriesVolumes() {
    }

    public static SourceBook.Builder withSeriesOf(final SourceBook.Builder builder, final HardcoverBookNode node) {
        final List<SeriesEntry> series =
            entries(Objects.requireNonNullElse(node.bookSeries(), List.of()), node.title());
        if (series.isEmpty()) {
            return builder;
        }
        final SeriesEntry primary = series.getFirst();
        return builder.seriesName(primary.name()).seriesPosition(primary.position()).series(series);
    }

    private static List<SeriesEntry> entries(
        final List<HardcoverBookNode.SeriesMembership> all, final @Nullable String title) {
        final List<HardcoverBookNode.SeriesMembership> memberships = withoutOneBookSeries(all);
        return primaryMembership(memberships, title)
            .map(primary -> Stream.concat(Stream.of(primary), memberships.stream()
                    .filter(membership -> BY_BOOK_COUNT.compare(membership, primary) > 0
                        && isVolume(membership) && !isIssueRun(membership, title)))
                .map(HardcoverSeriesVolumes::toEntry)
                .toList())
            .orElse(List.of());
    }

    private static Optional<HardcoverBookNode.SeriesMembership> primaryMembership(
        final List<HardcoverBookNode.SeriesMembership> memberships, final @Nullable String title) {
        if (memberships.isEmpty()) {
            return Optional.empty();
        }
        final HardcoverBookNode.SeriesMembership primary = memberships.stream()
            .filter(entry -> Boolean.TRUE.equals(entry.featured()))
            .max(BY_BOOK_COUNT)
            .orElse(memberships.getFirst());
        if (!isIssueRun(primary, title)) {
            return Optional.of(primary).filter(HardcoverSeriesVolumes::isVolume);
        }
        return memberships.stream()
            .filter(entry -> isVolume(entry) && !isIssueRun(entry, title))
            .findFirst();
    }

    private static SeriesEntry toEntry(final HardcoverBookNode.SeriesMembership membership) {
        final HardcoverBookNode.Series series = Objects.requireNonNull(membership.series());
        return new SeriesEntry(
            Objects.requireNonNull(series.name()), Objects.requireNonNull(membership.position()));
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
