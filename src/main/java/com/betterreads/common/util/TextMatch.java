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
}
