package com.betterreads.catalog.service.source.quality;

import java.util.Locale;

public final class IssueRunSeries {

    private static final String SINGLE_ISSUES = "(single issues)";

    private static final String SUBTITLE_SEPARATOR = ":";

    private IssueRunSeries() {
    }

    public static boolean matches(final String seriesName, final String bookTitle) {
        final String series = seriesName.toLowerCase(Locale.ROOT).strip();
        if (series.contains(SINGLE_ISSUES)) {
            return true;
        }
        final int separator = series.lastIndexOf(SUBTITLE_SEPARATOR);
        return separator >= 0
            && series.substring(separator + 1).strip().equals(bookTitle.toLowerCase(Locale.ROOT).strip());
    }
}
