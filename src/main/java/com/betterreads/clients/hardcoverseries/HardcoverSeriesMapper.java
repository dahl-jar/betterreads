package com.betterreads.clients.hardcoverseries;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceSeries;
import com.betterreads.booksource.SourceSeriesVolume;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverBookNodeMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Maps a Hardcover series hit and its volume list to a series.
 *
 * <p>Hardcover lists every edition and translation at each position, so each position from 0 to the
 * primary book count, prequels at 0 and novellas like 2.5 included, keeps its most-read English canonical
 * single book. Positions with no such book are dropped.
 */
@Component
class HardcoverSeriesMapper {

    private static final double PREQUEL_VOLUME = 0;

    /**
     * Returns the series, or null when the hit has no name or author, the series lists zero books,
     * or no volume survives.
     */
    public @Nullable SourceSeries toSourceSeries(
        final SeriesSearchDocument hit,
        final SeriesEnumerationResponse.Series enumerated
    ) {
        final String name = hit.name();
        final String author = hit.authorName();
        if (name == null || author == null || isEmptyContainer(enumerated)) {
            return null;
        }
        final List<SourceSeriesVolume> volumes = collapse(enumerated);
        return volumes.isEmpty() ? null : new SourceSeries(name, author, volumes);
    }

    private static boolean isEmptyContainer(final SeriesEnumerationResponse.Series series) {
        return Integer.valueOf(0).equals(series.primaryBooksCount());
    }

    private static List<SourceSeriesVolume> collapse(final SeriesEnumerationResponse.Series series) {
        final int cap = Objects.requireNonNullElse(series.primaryBooksCount(), Integer.MAX_VALUE);
        final List<SeriesEnumerationResponse.BookSeries> rows =
            Objects.requireNonNullElse(series.bookSeries(), List.of());

        final Map<Double, Candidate> best = rows.stream()
            .flatMap(row -> candidate(row, cap).stream())
            .collect(Collectors.toMap(Candidate::position, Function.identity(),
                BinaryOperator.maxBy(Comparator.comparingInt(Candidate::readers)), TreeMap::new));
        return best.values().stream()
            .map(candidate -> new SourceSeriesVolume(candidate.position(), candidate.book()))
            .toList();
    }

    private static Optional<Candidate> candidate(final SeriesEnumerationResponse.BookSeries row, final int cap) {
        final HardcoverBookNode node = row.book();
        if (node == null) {
            return Optional.empty();
        }
        return volume(row.position())
            .filter(volume -> volume <= cap)
            .flatMap(volume -> HardcoverBookNodeMapper.toSourceBookWithSeries(node)
                .map(book -> new Candidate(volume, book, HardcoverBookNodeMapper.readers(node))));
    }

    private static Optional<Double> volume(final @Nullable Double position) {
        return Optional.ofNullable(position).filter(volume -> volume >= PREQUEL_VOLUME);
    }

    private record Candidate(double position, SourceBook book, int readers) {
    }
}
