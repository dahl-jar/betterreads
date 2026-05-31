package com.betterreads.catalog.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Canonical genre vocabulary for reducing external subject strings to shelf genres. The terms are
 * the top-level BISAC genre headings.
 */
public final class CatalogGenres {

    private static final Set<String> GENRE_TERMS = Set.of(
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

    private CatalogGenres() {
    }

    /**
     * Returns the canonical genre terms a subject mentions, or empty for none.
     *
     * <p>Matching is word-boundary, not substring, so a machine tag like
     * {@code nyt:trade_fiction_paperback} does not register as {@code fiction}; subjects carrying
     * {@code :} or {@code =} are rejected as machine tags outright.
     */
    public static Set<String> extractGenres(final @Nullable String subject) {
        if (subject == null || subject.isBlank() || isMachineTag(subject)) {
            return Set.of();
        }
        final String lower = normalizeSeparators(subject.toLowerCase(Locale.ROOT));
        final Set<String> matched = new LinkedHashSet<>();
        for (final String term : GENRE_TERMS) {
            if (matchesAsWord(lower, term) && !subsumedByLongerMatch(lower, term)) {
                matched.add(term);
            }
        }
        return matched;
    }

    /** Returns true if the subject mentions at least one canonical genre. */
    public static boolean isGenre(final @Nullable String subject) {
        return !extractGenres(subject).isEmpty();
    }

    /**
     * Reduces a list of raw subject strings to canonical genres, deduplicated and capped.
     *
     * <p>A null input returns an empty list. The caller maps "field absent" to null itself, since
     * null and empty have different persistence semantics on {@code Book}.
     */
    public static List<String> reduceToCanonical(final @Nullable List<String> subjects, final int cap) {
        if (subjects == null) {
            return List.of();
        }
        final Set<String> canonical = new LinkedHashSet<>();
        for (final String subject : subjects) {
            canonical.addAll(extractGenres(subject));
            if (canonical.size() >= cap) {
                break;
            }
        }
        return new ArrayList<>(canonical);
    }

    private static boolean isMachineTag(final String subject) {
        return subject.indexOf(':') >= 0 || subject.indexOf('=') >= 0;
    }

    /** Collapses hyphens and slashes to spaces so {@code science-fiction} matches the canonical term. */
    private static String normalizeSeparators(final String subject) {
        return subject.replace('-', ' ').replace('/', ' ').replace('_', ' ');
    }

    private static boolean subsumedByLongerMatch(final String lower, final String term) {
        for (final String other : GENRE_TERMS) {
            if (other.length() > term.length()
                && other.contains(term)
                && matchesAsWord(lower, other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAsWord(final String haystack, final String term) {
        int from = 0;
        while (true) {
            final int matchAt = haystack.indexOf(term, from);
            if (matchAt < 0) {
                return false;
            }
            final boolean leftBoundary =
                matchAt == 0 || !Character.isLetter(haystack.charAt(matchAt - 1));
            if (leftBoundary && endsOnWordBoundary(haystack, matchAt + term.length())) {
                return true;
            }
            from = matchAt + 1;
        }
    }

    /** True if the match ends on a word boundary, allowing a trailing {@code s} for plural labels. */
    private static boolean endsOnWordBoundary(final String haystack, final int end) {
        if (end == haystack.length() || !Character.isLetter(haystack.charAt(end))) {
            return true;
        }
        return haystack.charAt(end) == 's'
            && (end + 1 == haystack.length() || !Character.isLetter(haystack.charAt(end + 1)));
    }
}
