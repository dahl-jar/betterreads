package com.betterreads.booksource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/** Reduces external subject strings to a fixed list of shelf genres. */
public final class CatalogGenres {

    private static final List<String> GENRE_TERMS = List.of(
        "fiction",
        "nonfiction",
        "fantasy",
        "science fiction",
        "mystery",
        "thriller",
        "romance",
        "horror",
        "classics",
        "dystopian",
        "graphic novel",
        "comics",
        "poetry",
        "biography",
        "memoir",
        "history",
        "philosophy",
        "young adult");

    /** a trailing {@code s} still counts so plural labels like {@code Graphic novels} match */
    private static final Map<String, Pattern> WORD_PATTERNS = GENRE_TERMS.stream()
        .collect(Collectors.toUnmodifiableMap(
            Function.identity(),
            term -> Pattern.compile("(?<!\\p{L})" + Pattern.quote(term) + "s?(?!\\p{L})")));

    private CatalogGenres() {
    }

    /**
     * machine tags like {@code nyt:trade_fiction_paperback} embed genre words, so subjects with
     * {@code :} or {@code =} are dropped and terms only match on word boundaries
     */
    static Set<String> extractGenres(final @Nullable String subject) {
        if (subject == null || subject.isBlank() || isMachineTag(subject)) {
            return Set.of();
        }
        final String lower = normalizeSeparators(subject.toLowerCase(Locale.ROOT));
        return GENRE_TERMS.stream()
            .filter(term -> matchesAsWord(lower, term) && !subsumedByLongerMatch(lower, term))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** distinct genres in subject order, empty for null subjects */
    public static List<String> reduceToCanonical(final @Nullable List<String> subjects) {
        if (subjects == null) {
            return List.of();
        }
        return subjects.stream()
            .flatMap(subject -> extractGenres(subject).stream())
            .distinct()
            .toList();
    }

    private static boolean isMachineTag(final String subject) {
        return subject.indexOf(':') >= 0 || subject.indexOf('=') >= 0;
    }

    /** OpenLibrary writes {@code Science-fiction}, so separators become spaces before matching */
    private static String normalizeSeparators(final String subject) {
        return subject.replace('-', ' ').replace('/', ' ').replace('_', ' ');
    }

    private static boolean subsumedByLongerMatch(final String lower, final String term) {
        return GENRE_TERMS.stream().anyMatch(other -> other.length() > term.length()
            && other.contains(term)
            && matchesAsWord(lower, other));
    }

    private static boolean matchesAsWord(final String haystack, final String term) {
        return Objects.requireNonNull(WORD_PATTERNS.get(term)).matcher(haystack).find();
    }
}
