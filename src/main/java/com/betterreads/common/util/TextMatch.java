package com.betterreads.common.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Case-insensitive text comparisons shared by the source clients' title-drift guards. */
public final class TextMatch {

    private static final Pattern SUBTITLE_TAIL = Pattern.compile("[:(].*$", Pattern.DOTALL);

    private TextMatch() {
    }

    /** Returns true if {@code haystack} contains {@code needle}, ignoring case. */
    public static boolean containsIgnoreCase(final String haystack, final String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns true when the title equals the query or the query is the more specific string.
     *
     * <p>A query carrying the author or extra words ("the martian weir") matches the canonical title
     * ("The Martian"); a title that extends the query ("The Sandman - Overture" for "the sandman")
     * does not.
     */
    public static boolean titleWithinQuery(final String title, final String query) {
        final String trimmedTitle = title.trim();
        final String trimmedQuery = query.trim();
        return trimmedTitle.equalsIgnoreCase(trimmedQuery)
            || containsIgnoreCase(trimmedQuery, trimmedTitle);
    }

    /**
     * Returns true when two titles share the same core, ignoring case and a trailing subtitle.
     *
     * <p>The core is the title up to the first colon or parenthesis, so "Dune" matches "Dune: A
     * Novel" and "Dune (novel)". A sequel that only shares a leading word, like "Dune Messiah", does
     * not match "Dune", which a substring check would wrongly accept.
     */
    public static boolean canonicalTitleMatches(final String first, final String second) {
        return coreTitle(first).equals(coreTitle(second));
    }

    private static String coreTitle(final String title) {
        return SUBTITLE_TAIL.matcher(title).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }
}
