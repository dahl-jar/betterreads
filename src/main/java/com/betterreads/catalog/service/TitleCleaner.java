package com.betterreads.catalog.service;

import java.util.regex.Pattern;

/**
 * Strips edition and series tags from a source title so the catalog stores the work title.
 *
 * <p>Sources append tags the catalog should not show: Google adds {@code (2019 Edition)}, edition
 * records add {@code (Series, Book N)}. Only a trailing tag carrying an edition or book-number
 * marker is removed; a bare parenthetical or a real subtitle is ambiguous and kept.
 */
public final class TitleCleaner {

    private static final Pattern EDITION_PARENTHETICAL =
        Pattern.compile("\\s*\\([^()]*(?:edition|book\\s+\\d+)[^()]*\\)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EDITION_SUBTITLE =
        Pattern.compile("\\s*:\\s*[^:]*edition\\s*$", Pattern.CASE_INSENSITIVE);

    private TitleCleaner() {
    }

    /** Returns the title with a trailing edition tag removed. */
    public static String clean(final String title) {
        final String withoutParenthetical = EDITION_PARENTHETICAL.matcher(title).replaceAll("");
        return EDITION_SUBTITLE.matcher(withoutParenthetical).replaceAll("").strip();
    }
}
