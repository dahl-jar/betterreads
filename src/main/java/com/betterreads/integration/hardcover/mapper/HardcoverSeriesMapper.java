package com.betterreads.integration.hardcover.mapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.source.SourceSeries;
import com.betterreads.catalog.service.source.SourceSeriesVolume;
import com.betterreads.integration.hardcover.dto.HardcoverBookNode;
import com.betterreads.integration.hardcover.dto.SeriesEnumerationResponse;
import com.betterreads.integration.hardcover.dto.SeriesSearchDocument;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link SourceSeries} from a Hardcover Series search hit and its volume enumeration.
 *
 * <p>The enumeration lists every edition and translation at each position. One volume survives per
 * integer position 1 to the series' primary book count: the most-read English canonical single book
 * at that position. Each surviving volume carries the fields the enumeration returned, so the seed
 * needs no further Hardcover call. Positions with no qualifying book are dropped.
 */
@Component
public class HardcoverSeriesMapper {

    /**
     * Returns the series, or null when the search hit or the enumeration cannot supply a name,
     * author, and at least one volume.
     */
    public @Nullable SourceSeries toSourceSeries(
        final SeriesSearchDocument hit,
        final SeriesEnumerationResponse.@Nullable Series enumerated
    ) {
        final String name = hit.name();
        final String author = hit.authorName();
        if (name == null || author == null || enumerated == null) {
            return null;
        }
        final List<SourceSeriesVolume> volumes = collapse(enumerated, name);
        return volumes.isEmpty() ? null : new SourceSeries(name, author, volumes);
    }

    private static List<SourceSeriesVolume> collapse(
        final SeriesEnumerationResponse.Series series,
        final String name
    ) {
        final int cap = series.primaryBooksCount() == null ? Integer.MAX_VALUE
            : series.primaryBooksCount();
        final List<SeriesEnumerationResponse.BookSeries> rows =
            series.bookSeries() == null ? List.of() : series.bookSeries();

        final Map<Integer, Candidate> best = new TreeMap<>();
        for (final SeriesEnumerationResponse.BookSeries row : rows) {
            final Optional<Integer> position = integerPosition(row.position())
                .filter(p -> p >= 1 && p <= cap);
            if (position.isEmpty()) {
                continue;
            }
            keepMostRead(best, position.get(), row.book(), name);
        }
        return best.entrySet().stream()
            .map(entry -> new SourceSeriesVolume(entry.getKey(), entry.getValue().book()))
            .toList();
    }

    private static void keepMostRead(
        final Map<Integer, Candidate> best,
        final int position,
        final @Nullable HardcoverBookNode node,
        final String name
    ) {
        if (node == null) {
            return;
        }
        HardcoverBookNodeMapper.toBuilder(node).ifPresent(builder -> {
            final int readers = HardcoverBookNodeMapper.readers(node);
            final Candidate current = best.get(position);
            if (current == null || readers > current.readers()) {
                final SourceBook book = builder.seriesName(name).seriesPosition(position).build();
                best.put(position, new Candidate(book, readers));
            }
        });
    }

    private record Candidate(SourceBook book, int readers) {
    }

    private static Optional<Integer> integerPosition(final @Nullable Double position) {
        if (position == null || Double.compare(position, Math.floor(position)) != 0) {
            return Optional.empty();
        }
        return Optional.of(position.intValue());
    }
}
