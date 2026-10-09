package com.betterreads.clients.hardcoverseries;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.betterreads.booksource.SourceBook;
import com.betterreads.booksource.SourceSeries;
import com.betterreads.booksource.SourceSeriesVolume;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverBookNodeMapper;
import com.betterreads.text.TextMatch;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class HardcoverSeriesMapper {

    private static final double PREQUEL_VOLUME = 0;

    private static final double MIN_TITLE_BOOK_SHARE = 0.2;

    private static final Comparator<HardcoverBookNode> BY_READERS =
        Comparator.comparingInt(HardcoverBookNodeMapper::readers);

    public @Nullable SourceSeries toSourceSeries(
        final SeriesSearchDocument hit,
        final SeriesEnumerationResponse.Series enumerated
    ) {
        final String name = hit.name();
        final String author = hit.authorName();
        if (name == null || author == null || isEmptyContainer(enumerated)) {
            return null;
        }
        final List<Candidate> volumes = collapse(enumerated);
        if (volumes.isEmpty()) {
            return null;
        }
        return new SourceSeries(name, author,
            volumes.stream().map(candidate -> new SourceSeriesVolume(candidate.position(), candidate.book())).toList(),
            titleBook(name, enumerated, volumes));
    }

    private static boolean isEmptyContainer(final SeriesEnumerationResponse.Series series) {
        return Integer.valueOf(0).equals(series.primaryBooksCount());
    }

    private static List<SeriesEnumerationResponse.BookSeries> rows(final SeriesEnumerationResponse.Series series) {
        return Objects.requireNonNullElse(series.bookSeries(), List.of());
    }

    private static List<Candidate> collapse(final SeriesEnumerationResponse.Series series) {
        final int cap = Objects.requireNonNullElse(series.primaryBooksCount(), Integer.MAX_VALUE);
        final Map<Double, Candidate> best = rows(series).stream()
            .flatMap(row -> candidate(row, cap).stream())
            .collect(Collectors.toMap(Candidate::position, Function.identity(),
                BinaryOperator.maxBy(Comparator.comparingInt(Candidate::readers)), TreeMap::new));
        return List.copyOf(best.values());
    }

    private static @Nullable SourceBook titleBook(
        final String name,
        final SeriesEnumerationResponse.Series series,
        final List<Candidate> volumes
    ) {
        final Set<String> volumeIds = volumes.stream()
            .map(candidate -> candidate.book().hardcoverId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        final int topReaders = volumes.stream().mapToInt(Candidate::readers).max().orElse(0);
        return rows(series).stream()
            .map(SeriesEnumerationResponse.BookSeries::book)
            .filter(Objects::nonNull)
            .filter(node -> node.title() != null && TextMatch.canonicalTitleMatches(node.title(), name))
            .filter(node -> isWellRead(node, topReaders))
            .sorted(BY_READERS.reversed())
            .flatMap(node -> HardcoverBookNodeMapper.toTitleBook(node).stream())
            .findFirst()
            .filter(book -> !volumeIds.contains(book.hardcoverId()))
            .orElse(null);
    }

    private static boolean isWellRead(final HardcoverBookNode node, final int topReaders) {
        return topReaders > 0
            && (double) HardcoverBookNodeMapper.readers(node) / topReaders >= MIN_TITLE_BOOK_SHARE;
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
