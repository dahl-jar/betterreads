package com.betterreads.common.util;

import java.util.Locale;

/** Case-insensitive text comparisons shared by the source clients' title-drift guards. */
public final class TextMatch {

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
}
