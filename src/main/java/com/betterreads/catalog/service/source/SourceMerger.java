package com.betterreads.catalog.service.source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Combines several single-source books into one, resolving each field by the priority order in
 * {@code source-trust.md}.
 *
 * <p>Subjects are unioned across the live sources; a staged seed's subjects count only when no live
 * source carries any. Every other field takes the first source in its priority chain that supplies
 * a value, so a higher-priority source missing a field yields to a lower-priority one that has it.
 * A staged seed sits last in each chain, so stored values yield to any live source but keep a book
 * promotable when every live fetch fails. Identifiers are carried from whichever source holds them.
 */
// PMD.TooManyMethods: an aggregator with one small private picker per field; splitting scatters the trust-order chains.
@SuppressWarnings("PMD.TooManyMethods")
@Component
public class SourceMerger {

    private static final List<BookFieldSource> TITLE_CHAIN = List.of(
        BookFieldSource.GOOGLE_BOOKS, BookFieldSource.OPEN_LIBRARY,
        BookFieldSource.HARDCOVER, BookFieldSource.WIKIDATA, BookFieldSource.LOC,
        BookFieldSource.STAGED);

    private static final List<BookFieldSource> SUBTITLE_CHAIN = List.of(
        BookFieldSource.GOOGLE_BOOKS, BookFieldSource.OPEN_LIBRARY, BookFieldSource.STAGED);

    private static final List<BookFieldSource> DESCRIPTION_CHAIN = List.of(
        BookFieldSource.GOOGLE_BOOKS, BookFieldSource.OPEN_LIBRARY,
        BookFieldSource.HARDCOVER, BookFieldSource.LOC, BookFieldSource.STAGED);

    private static final List<BookFieldSource> COVER_CHAIN = List.of(
        BookFieldSource.GOOGLE_BOOKS, BookFieldSource.HARDCOVER, BookFieldSource.OPEN_LIBRARY,
        BookFieldSource.STAGED);

    private static final List<BookFieldSource> YEAR_CHAIN = List.of(
        BookFieldSource.OPEN_LIBRARY, BookFieldSource.GOOGLE_BOOKS,
        BookFieldSource.WIKIDATA, BookFieldSource.LOC, BookFieldSource.HARDCOVER);

    private static final List<BookFieldSource> PUBLISHER_CHAIN =
        List.of(BookFieldSource.GOOGLE_BOOKS, BookFieldSource.STAGED);

    private static final List<BookFieldSource> PAGE_COUNT_CHAIN = List.of(
        BookFieldSource.GOOGLE_BOOKS, BookFieldSource.HARDCOVER, BookFieldSource.LOC,
        BookFieldSource.STAGED);

    private static final List<BookFieldSource> LANGUAGE_CHAIN = List.of(
        BookFieldSource.GOOGLE_BOOKS, BookFieldSource.OPEN_LIBRARY, BookFieldSource.LOC,
        BookFieldSource.STAGED);

    private static final List<BookFieldSource> AUTHORS_CHAIN =
        List.of(BookFieldSource.OPEN_LIBRARY, BookFieldSource.GOOGLE_BOOKS,
            BookFieldSource.WIKIDATA, BookFieldSource.HARDCOVER, BookFieldSource.LOC,
            BookFieldSource.STAGED);

    /** No staged entry: {@code keepStagedRating} restores a staged rating on a Hardcover miss. */
    private static final List<BookFieldSource> RATING_CHAIN = List.of(BookFieldSource.HARDCOVER);

    /** No staged entry: a stale stored series must not outlive the live authorities' answer. */
    private static final List<BookFieldSource> SERIES_CHAIN =
        List.of(BookFieldSource.HARDCOVER, BookFieldSource.WIKIDATA);

    private static final List<BookFieldSource> AWARDS_CHAIN =
        List.of(BookFieldSource.WIKIDATA, BookFieldSource.STAGED);

    private static final List<BookFieldSource> ISBN_CHAIN = List.of(
        BookFieldSource.GOOGLE_BOOKS, BookFieldSource.OPEN_LIBRARY,
        BookFieldSource.LOC, BookFieldSource.HARDCOVER, BookFieldSource.STAGED);

    /** Merges the given books into one, or throws when no source carries a title. */
    public MergedBook merge(final List<SourceBook> sources) {
        return merge(null, sources);
    }

    /**
     * Merges the given books into one, preferring the seed's year.
     *
     * <p>The seed resolved the work itself: a discovery hit (the Hardcover series or author path) or
     * the staged row being re-collected. The other sources resolve an edition by title or ISBN and
     * drift to a reprint year, so when the seed carries a year it wins over {@link #YEAR_CHAIN};
     * otherwise the chain resolves the year.
     */
    public MergedBook merge(final @Nullable SourceBook seed, final List<SourceBook> sources) {
        final Map<BookFieldSource, SourceBook> bySource = new EnumMap<>(BookFieldSource.class);
        for (final SourceBook source : sources) {
            bySource.put(source.source(), source);
        }

        final Winner<String> title = pick(bySource, TITLE_CHAIN, SourceMerger::usableText, SourceBook::title);
        if (title == null) {
            throw new IllegalArgumentException(
                "no source supplied a title; the merged book would be unshowable");
        }
        final Resolved resolved = new Resolved(
            title,
            pickBestDescription(bySource),
            pick(bySource, COVER_CHAIN, SourceMerger::usableText, SourceBook::coverUrl),
            resolveYear(seed, bySource),
            unionSubjects(bySource));

        final SourceBook merged = assemble(bySource, sources, resolved);
        return new MergedBook(
            merged, resolved.provenance(), resolved.subjects().sources(), bySource.keySet());
    }

    private static @Nullable Winner<Integer> resolveYear(
        final @Nullable SourceBook seed,
        final Map<BookFieldSource, SourceBook> bySource
    ) {
        if (seed != null && seed.publicationYear() != null) {
            return new Winner<>(seed.publicationYear(), seed.source());
        }
        return pick(bySource, YEAR_CHAIN, SourceBook::publicationYear);
    }

    private static SourceBook assemble(
        final Map<BookFieldSource, SourceBook> bySource,
        final List<SourceBook> sources,
        final Resolved resolved
    ) {
        final Winner<String> title = resolved.title();
        final Subjects subjects = resolved.subjects();
        final Optional<SourceBook> series = pickSeries(bySource);
        return SourceBook.builder(title.source())
            .title(TitleCleaner.clean(title.value()))
            .subtitle(valueOf(pick(bySource, SUBTITLE_CHAIN, SourceMerger::usableText, SourceBook::subtitle)))
            .description(valueOf(resolved.description()))
            .coverUrl(valueOf(resolved.cover()))
            .publicationYear(valueOf(resolved.year()))
            .publisher(valueOf(pick(bySource, PUBLISHER_CHAIN, SourceMerger::usableText, SourceBook::publisher)))
            .pageCount(valueOf(pick(bySource, PAGE_COUNT_CHAIN, SourceBook::pageCount)))
            .language(valueOf(pick(bySource, LANGUAGE_CHAIN, SourceMerger::usableText, SourceBook::language)))
            .authors(valueOf(pick(bySource, AUTHORS_CHAIN, SourceMerger::nonEmpty, SourceBook::authors)))
            .rawSubjects(subjects.values().isEmpty() ? null : subjects.values())
            .awards(valueOf(pick(bySource, AWARDS_CHAIN, SourceMerger::nonEmpty, SourceBook::awards)))
            .averageRating(valueOf(pick(bySource, RATING_CHAIN, SourceBook::averageRating)))
            .ratingCount(valueOf(pick(bySource, RATING_CHAIN, SourceBook::ratingCount)))
            .seriesName(series.map(SourceBook::seriesName).orElse(null))
            .seriesPosition(series.map(SourceBook::seriesPosition).orElse(null))
            .isbn13(valueOf(pick(bySource, ISBN_CHAIN, SourceMerger::usableText, SourceBook::isbn13)))
            .googleBooksVolumeId(firstId(sources, SourceBook::googleBooksVolumeId))
            .openLibraryWorkKey(firstId(sources, SourceBook::openLibraryWorkKey))
            .hardcoverId(firstId(sources, SourceBook::hardcoverId))
            .locLccn(firstId(sources, SourceBook::locLccn))
            .wikidataQid(firstId(sources, SourceBook::wikidataQid))
            .build();
    }

    private static <T> @Nullable Winner<T> pick(
        final Map<BookFieldSource, SourceBook> bySource,
        final List<BookFieldSource> chain,
        final Predicate<T> usable,
        final Function<SourceBook, T> field
    ) {
        for (final BookFieldSource source : chain) {
            final SourceBook book = bySource.get(source);
            if (book == null) {
                continue;
            }
            final T value = field.apply(book);
            if (value != null && usable.test(value)) {
                return new Winner<>(value, source);
            }
        }
        return null;
    }

    /** Picks the first source in the chain whose field is non-null, for fields with no blank state. */
    private static <T> @Nullable Winner<T> pick(
        final Map<BookFieldSource, SourceBook> bySource,
        final List<BookFieldSource> chain,
        final Function<SourceBook, T> field
    ) {
        return pick(bySource, chain, value -> true, field);
    }

    /**
     * Picks the source whose series tag is a numbered volume: a name and an integer position
     * together. A source with a name but no position is a companion or guide grouped under the
     * series, not a volume, so it is skipped and the series stays unset.
     */
    private static Optional<SourceBook> pickSeries(final Map<BookFieldSource, SourceBook> bySource) {
        return SERIES_CHAIN.stream()
            .map(bySource::get)
            .filter(book -> book != null
                && book.seriesName() != null && !book.seriesName().isBlank()
                && book.seriesPosition() != null)
            .findFirst();
    }

    /**
     * Unions the live sources' subjects, falling back to the staged seed's when no live source
     * carries any, so a fresh collect can shrink the stored set instead of re-accumulating it.
     */
    private static Subjects unionSubjects(final Map<BookFieldSource, SourceBook> bySource) {
        final Set<String> values = new LinkedHashSet<>();
        final Set<BookFieldSource> contributors = new LinkedHashSet<>();
        for (final SourceBook source : bySource.values()) {
            if (source.source() != BookFieldSource.STAGED) {
                addSubjects(source, values, contributors);
            }
        }
        if (values.isEmpty()) {
            final SourceBook staged = bySource.get(BookFieldSource.STAGED);
            if (staged != null) {
                addSubjects(staged, values, contributors);
            }
        }
        return new Subjects(new ArrayList<>(values), contributors);
    }

    private static void addSubjects(
        final SourceBook source,
        final Set<String> values,
        final Set<BookFieldSource> contributors
    ) {
        final List<String> subjects = source.rawSubjects();
        if (subjects != null && !subjects.isEmpty()) {
            values.addAll(subjects);
            contributors.add(source.source());
        }
    }

    private static @Nullable String firstId(
        final List<SourceBook> sources, final Function<SourceBook, @Nullable String> idOf) {
        return sources.stream().map(idOf).filter(value -> value != null).findFirst().orElse(null);
    }

    private static boolean usableText(final String value) {
        return !value.isBlank();
    }

    /**
     * Picks the highest-quality description across the chain and returns it cleaned of markup.
     *
     * <p>A source's raw description is assessed by {@link DescriptionQuality}; a dump, a stub, or
     * boilerplate scores as unusable and is skipped. The chain is walked in priority order so a score
     * tie breaks toward the higher-priority source.
     */
    private static @Nullable Winner<String> pickBestDescription(
        final Map<BookFieldSource, SourceBook> bySource
    ) {
        return DESCRIPTION_CHAIN.stream()
            .map(bySource::get)
            .filter(book -> book != null && book.description() != null)
            .map(book -> new Scored(book.source(), DescriptionQuality.assess(book.description())))
            .filter(scored -> scored.assessment().usable())
            .max(Comparator.comparingInt(scored -> scored.assessment().score()))
            .map(scored -> new Winner<>(scored.assessment().cleaned(), scored.source()))
            .orElse(null);
    }

    private record Scored(BookFieldSource source, DescriptionQuality.Assessment assessment) {
    }

    private static <T> boolean nonEmpty(final List<T> value) {
        return !value.isEmpty();
    }

    private static <T> @Nullable T valueOf(final @Nullable Winner<T> winner) {
        return winner == null ? null : winner.value();
    }

    private record Winner<T>(T value, BookFieldSource source) {
    }

    private record Subjects(List<String> values, Set<BookFieldSource> sources) {

        Optional<BookFieldSource> firstContributor() {
            return sources.stream().findFirst();
        }
    }

    private record Resolved(
        Winner<String> title,
        @Nullable Winner<String> description,
        @Nullable Winner<String> cover,
        @Nullable Winner<Integer> year,
        Subjects subjects
    ) {

        Map<BookField, BookFieldSource> provenance() {
            final Map<BookField, BookFieldSource> sources = new EnumMap<>(BookField.class);
            sources.put(BookField.TITLE, title.source());
            put(sources, BookField.DESCRIPTION, description);
            put(sources, BookField.COVER, cover);
            put(sources, BookField.PUBLICATION_YEAR, year);
            subjects.firstContributor().ifPresent(source -> sources.put(BookField.SUBJECTS, source));
            return sources;
        }

        private static void put(
            final Map<BookField, BookFieldSource> sources,
            final BookField field,
            final @Nullable Winner<?> winner
        ) {
            if (winner != null) {
                sources.put(field, winner.source());
            }
        }
    }
}
