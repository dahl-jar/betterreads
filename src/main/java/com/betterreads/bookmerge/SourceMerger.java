package com.betterreads.bookmerge;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.betterreads.bookdescription.DescriptionQuality;
import com.betterreads.booksource.BookField;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.IssueRunSeries;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import com.betterreads.isbn.IsbnLanguage;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

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
        List.of(BookFieldSource.HARDCOVER, BookFieldSource.GOOGLE_BOOKS,
            BookFieldSource.WIKIDATA, BookFieldSource.OPEN_LIBRARY, BookFieldSource.LOC,
            BookFieldSource.STAGED);

    private static final List<BookFieldSource> RATING_CHAIN = List.of(BookFieldSource.HARDCOVER);

    private static final List<BookFieldSource> SERIES_CHAIN =
        List.of(BookFieldSource.HARDCOVER, BookFieldSource.WIKIDATA);

    private static final List<BookFieldSource> AWARDS_CHAIN =
        List.of(BookFieldSource.WIKIDATA, BookFieldSource.STAGED);

    private static final List<BookFieldSource> ISBN_CHAIN = List.of(
        BookFieldSource.GOOGLE_BOOKS, BookFieldSource.OPEN_LIBRARY,
        BookFieldSource.LOC, BookFieldSource.HARDCOVER, BookFieldSource.STAGED);

    public MergedBook merge(final @Nullable SourceBook seed, final List<SourceBook> sources) {
        final Map<BookFieldSource, SourceBook> bySource = new EnumMap<>(BookFieldSource.class);
        for (final SourceBook source : sources) {
            bySource.put(source.source(), source);
        }

        final Winner<String> title = pick(bySource, TITLE_CHAIN, SourceMerger::usableText, SourceBook::title);
        if (title == null) {
            throw new IllegalArgumentException("no source supplied a title");
        }
        final Resolved resolved = new Resolved(
            title,
            pickBestDescription(bySource),
            pick(bySource, COVER_CHAIN, SourceMerger::usableText, SourceBook::coverUrl),
            resolveYear(seed, bySource),
            MergedSubjects.union(bySource));

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
        final MergedSubjects subjects = resolved.subjects();
        final @Nullable String isbn13 =
            valueOf(pick(bySource, ISBN_CHAIN, SourceMerger::usableText, SourceBook::isbn13));
        final @Nullable String language = resolveLanguage(bySource, isbn13);
        final String displayTitle =
            TitleCleaner.clean(TitleCasing.capitalize(
                TitleCleaner.closePunctuation(title.value()), titles(sources), language));
        final Optional<SourceBook> series = pickSeries(bySource, displayTitle);
        return SourceBook.builder(title.source())
            .title(displayTitle)
            .subtitle(valueOf(pick(bySource, SUBTITLE_CHAIN, SourceMerger::usableText, SourceBook::subtitle)))
            .description(valueOf(resolved.description()))
            .coverUrl(valueOf(resolved.cover()))
            .publicationYear(valueOf(resolved.year()))
            .publisher(valueOf(pick(bySource, PUBLISHER_CHAIN, SourceMerger::usableText, SourceBook::publisher)))
            .pageCount(valueOf(pick(bySource, PAGE_COUNT_CHAIN, SourceBook::pageCount)))
            .language(language)
            .authors(CoAuthors.addFrom(
                valueOf(pick(bySource, AUTHORS_CHAIN, Predicate.not(List::isEmpty), SourceBook::authors)),
                valueOf(pick(bySource, List.of(BookFieldSource.LOC), SourceBook::authors))))
            .rawSubjects(subjects.values().isEmpty() ? null : subjects.values())
            .awards(valueOf(pick(bySource, AWARDS_CHAIN, Predicate.not(List::isEmpty), SourceBook::awards)))
            .averageRating(valueOf(pick(bySource, RATING_CHAIN, SourceBook::averageRating)))
            .ratingCount(valueOf(pick(bySource, RATING_CHAIN, SourceBook::ratingCount)))
            .seriesName(series.map(SourceBook::seriesName).orElse(null))
            .seriesPosition(series.map(SourceBook::seriesPosition).orElse(null))
            .series(series.map(SourceBook::series).orElse(null))
            .isbn13(isbn13)
            .googleBooksVolumeId(firstId(sources, SourceBook::googleBooksVolumeId))
            .openLibraryWorkKey(firstId(sources, SourceBook::openLibraryWorkKey))
            .hardcoverId(firstId(sources, SourceBook::hardcoverId))
            .locLccn(firstId(sources, SourceBook::locLccn))
            .wikidataQid(firstId(sources, SourceBook::wikidataQid))
            .build();
    }

    private static @Nullable String resolveLanguage(
        final Map<BookFieldSource, SourceBook> bySource, final @Nullable String isbn13) {
        final String language = LanguageCodes.iso6391(
            valueOf(pick(bySource, LANGUAGE_CHAIN, SourceMerger::usableText, SourceBook::language)));
        return language != null ? language : IsbnLanguage.languageOf(isbn13);
    }

    private static List<String> titles(final List<SourceBook> sources) {
        return sources.stream()
            .map(SourceBook::title)
            .filter(title -> title != null)
            .map(TitleCleaner::closePunctuation)
            .toList();
    }

    private static <T> @Nullable Winner<T> pick(
        final Map<BookFieldSource, SourceBook> bySource,
        final List<BookFieldSource> chain,
        final Predicate<T> usable,
        final Function<SourceBook, T> field
    ) {
        return chain.stream()
            .flatMap(source -> Optional.ofNullable(bySource.get(source))
                .map(field)
                .filter(usable)
                .map(value -> new Winner<>(value, source))
                .stream())
            .findFirst()
            .orElse(null);
    }

    private static <T> @Nullable Winner<T> pick(
        final Map<BookFieldSource, SourceBook> bySource,
        final List<BookFieldSource> chain,
        final Function<SourceBook, T> field
    ) {
        return pick(bySource, chain, value -> true, field);
    }

    private static Optional<SourceBook> pickSeries(
        final Map<BookFieldSource, SourceBook> bySource, final String bookTitle) {
        return SERIES_CHAIN.stream()
            .map(bySource::get)
            .filter(book -> book != null
                && book.seriesName() != null && !book.seriesName().isBlank()
                && book.seriesPosition() != null
                && !IssueRunSeries.matches(book.seriesName(), bookTitle))
            .findFirst();
    }

    private static @Nullable String firstId(
        final List<SourceBook> sources, final Function<SourceBook, @Nullable String> idOf) {
        return sources.stream().map(idOf).filter(value -> value != null).findFirst().orElse(null);
    }

    private static boolean usableText(final String value) {
        return !value.isBlank();
    }

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

    private static <T> @Nullable T valueOf(final @Nullable Winner<T> winner) {
        return winner == null ? null : winner.value();
    }

    private record Winner<T>(T value, BookFieldSource source) {
    }

    private record Resolved(
        Winner<String> title,
        @Nullable Winner<String> description,
        @Nullable Winner<String> cover,
        @Nullable Winner<Integer> year,
        MergedSubjects subjects
    ) {

        Map<BookField, BookFieldSource> provenance() {
            final Map<BookField, BookFieldSource> sources = new EnumMap<>(BookField.class);
            sources.put(BookField.TITLE, title.source());
            put(sources, BookField.DESCRIPTION, description);
            put(sources, BookField.COVER, cover);
            put(sources, BookField.PUBLICATION_YEAR, year);
            subjects.sources().stream().findFirst().ifPresent(source -> sources.put(BookField.SUBJECTS, source));
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
