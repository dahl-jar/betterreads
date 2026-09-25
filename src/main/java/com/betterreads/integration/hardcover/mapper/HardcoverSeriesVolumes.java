package com.betterreads.integration.hardcover.mapper;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.catalog.service.source.quality.IssueRunSeries;
import com.betterreads.integration.hardcover.dto.HardcoverBookNode;
import org.jspecify.annotations.Nullable;

final class HardcoverSeriesVolumes {

    private HardcoverSeriesVolumes() {
    }

    static SourceBook.Builder withSeriesOf(final SourceBook.Builder builder, final HardcoverBookNode node) {
        final List<HardcoverBookNode.SeriesMembership> memberships = node.bookSeries();
        final HardcoverBookNode.SeriesMembership membership = memberships == null ? null
            : volumeMembership(memberships, node.title()).orElse(null);
        final HardcoverBookNode.Series series = membership == null ? null : membership.series();
        if (membership == null || series == null) {
            return builder;
        }
        return builder.seriesName(series.name()).seriesPosition(membership.position());
    }

    private static Optional<HardcoverBookNode.SeriesMembership> volumeMembership(
        final List<HardcoverBookNode.SeriesMembership> memberships, final @Nullable String title) {
        if (memberships.isEmpty()) {
            return Optional.empty();
        }
        final HardcoverBookNode.SeriesMembership featured = memberships.stream()
            .filter(entry -> Boolean.TRUE.equals(entry.featured()))
            .findFirst()
            .orElse(memberships.get(0));
        if (!isIssueRun(featured, title)) {
            return Optional.of(featured).filter(HardcoverSeriesVolumes::isVolume);
        }
        return memberships.stream()
            .filter(entry -> isVolume(entry) && !isIssueRun(entry, title))
            .findFirst();
    }

    static boolean isIssueRun(final @Nullable String seriesName, final @Nullable String title) {
        return seriesName != null && title != null && IssueRunSeries.matches(seriesName, title);
    }

    private static boolean isIssueRun(
        final HardcoverBookNode.SeriesMembership membership, final @Nullable String title) {
        return membership.series() != null && isIssueRun(membership.series().name(), title);
    }

    private static boolean isVolume(final HardcoverBookNode.SeriesMembership membership) {
        return membership.series() != null && membership.series().name() != null
            && membership.position() != null && membership.position() >= 1;
    }
}
