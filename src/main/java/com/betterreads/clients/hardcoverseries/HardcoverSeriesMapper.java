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
 * <p>Hardcover lists every edition and translation at each position, so each whole position from 1 to
 * the primary book count keeps its most-read English canonical single book. Positions with no such book
 * are dropped.
 */
@Component
class HardcoverSeriesMapper {

    private static final int FIRST_VOLUME = 1;

    /**
     * Returns the series, or null when the search hit or the enumeration cannot supply a name,
     * author, and at least one volume.
     */
    public @Nullable SourceSeries toSourceSeries(
        final SeriesSearchDocument hit,
        final SeriesEnumerationResponse.Series enumerated
    ) {
        final String name = hit.name();
        final String author = hit.authorName();
        if (name == null || author == null) {
            return null;
        }
        final List<SourceSeriesVolume> volumes = collapse(enumerated);
        return volumes.isEmpty() ? null : new SourceSeries(name, author, volumes);
    }

    private static List<SourceSeriesVolume> collapse(final SeriesEnumerationResponse.Series series) {
        final int cap = Objects.requireNonNullElse(series.primaryBooksCount(), Integer.MAX_VALUE);
        final List<SeriesEnumerationResponse.BookSeries> rows =
            Objects.requireNonNullElse(series.bookSeries(), List.of());

        final Map<Integer, Candidate> best = rows.stream()
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
        return wholeVolume(row.position())
            .filter(volume -> volume <= cap)
            .flatMap(volume -> HardcoverBookNodeMapper.toSourceBookWithSeries(node)
                .map(book -> new Candidate(volume, book, HardcoverBookNodeMapper.readers(node))));
    }

    private static Optional<Integer> wholeVolume(final @Nullable Double position) {
        if (position == null || Double.compare(position, Math.floor(position)) != 0) {
            return Optional.empty();
        }
        return Optional.of(position.intValue()).filter(volume -> volume >= FIRST_VOLUME);
    }

    private record Candidate(int position, SourceBook book, int readers) {
    }
}
