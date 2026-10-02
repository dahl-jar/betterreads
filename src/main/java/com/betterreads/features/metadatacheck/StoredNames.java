package com.betterreads.features.metadatacheck;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;

final class StoredNames {

    private static final Pattern LEADING_ARTICLE = Pattern.compile("^the\\s+", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]");

    private static final Pattern SUBTITLE_SEPARATOR = Pattern.compile(":| \\(| - ");

    private static final Pattern LAST_FIRST = Pattern.compile("^([^,]+),\\s*(.+)$");

    private static final Pattern NAME_SUFFIX = Pattern.compile("(?i)(jr|sr|ii|iii|iv)\\.?");

    private final Map<String, Optional<String>> series;

    private final Map<String, Optional<String>> authors;

    StoredNames(final List<String> seriesNames, final List<String> authorNames) {
        this.series = byKey(seriesNames, StoredNames::seriesKey);
        this.authors = byKey(authorNames, StoredNames::authorKey);
    }

    VerifiedMetadata withStoredSpelling(final VerifiedMetadata metadata, final String storedTitle) {
        final String seriesName = metadata.seriesName();
        final List<String> names = metadata.authors();
        return new VerifiedMetadata(
            title(metadata.title(), storedTitle),
            names == null ? null : names.stream().map(name -> stored(authors, authorKey(name), name)).toList(),
            metadata.year(),
            seriesName == null ? null : storedSeries(seriesName),
            metadata.seriesPosition(),
            metadata.description(),
            metadata.isbn13(),
            storedUniverse(seriesName, metadata.universe()));
    }

    private @Nullable SeriesEntry storedUniverse(
        final @Nullable String seriesName, final @Nullable SeriesEntry universe) {
        final boolean repeatsSeries = universe != null && seriesName != null
            && seriesKey(universe.name()).equals(seriesKey(seriesName));
        return universe == null || repeatsSeries
            ? null
            : new SeriesEntry(storedSeries(universe.name()), universe.position());
    }

    private String storedSeries(final String name) {
        return stored(series, seriesKey(name), name);
    }

    private static String stored(final Map<String, Optional<String>> byKey, final String key, final String name) {
        return byKey.getOrDefault(key, Optional.empty()).orElse(name);
    }

    private static @Nullable String title(final @Nullable String verified, final String stored) {
        if (verified == null) {
            return null;
        }
        final String verifiedKey = key(verified);
        final String storedKey = key(stored);
        final boolean sameBook = verifiedKey.equals(storedKey)
            || key(coreTitle(verified)).equals(storedKey)
            || verifiedKey.equals(key(coreTitle(stored)));
        return sameBook && !verifiedKey.isEmpty() ? stored : verified;
    }

    private static String coreTitle(final String title) {
        return SUBTITLE_SEPARATOR.split(title, 2)[0];
    }

    private static Map<String, Optional<String>> byKey(
        final List<String> names, final Function<String, String> keyOf) {
        return names.stream()
            .filter(name -> !keyOf.apply(name).isEmpty())
            .collect(Collectors.toMap(keyOf, Optional::of, (first, second) -> Optional.empty()));
    }

    private static String seriesKey(final String name) {
        return key(LEADING_ARTICLE.matcher(name.strip()).replaceFirst(""));
    }

    private static String authorKey(final String name) {
        final Matcher lastFirst = LAST_FIRST.matcher(name.strip());
        final boolean swap = lastFirst.matches() && !NAME_SUFFIX.matcher(lastFirst.group(2).strip()).matches();
        return key(swap ? lastFirst.group(2) + " " + lastFirst.group(1) : name);
    }

    private static String key(final String text) {
        final String composed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        return NOT_LETTER_OR_DIGIT.matcher(composed).replaceAll("");
    }
}
