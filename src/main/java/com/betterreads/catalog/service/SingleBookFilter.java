package com.betterreads.catalog.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides whether a search hit is a single book rather than a collection.
 *
 * <p>A series search returns single novels alongside boxed sets, multi-volume omnibuses, and books
 * split into part editions. Only single books belong in the catalog, so the collections and splits
 * are filtered out before staging. A numbered series volume like {@code (Book 7)} is a single book
 * and is kept; only a multi-volume range like {@code Books 1-4} is rejected.
 */
public final class SingleBookFilter {

    private static final List<Pattern> COLLECTION_MARKERS = List.of(
        Pattern.compile("box(?:ed)?\\s+set", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:books|volumes)\\s+\\d+\\s*-\\s*\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("complete\\b.*\\bset", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\(part\\s+\\d+/\\d+\\)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpart\\s+(?:one|two|three|four|five|\\d+)\\b", Pattern.CASE_INSENSITIVE));

    private SingleBookFilter() {
    }

    /** Returns true when the title is a single book, false for a boxed set, omnibus, or part split. */
    public static boolean isSingleBook(final String title) {
        return COLLECTION_MARKERS.stream().noneMatch(marker -> marker.matcher(title).find());
    }
}
