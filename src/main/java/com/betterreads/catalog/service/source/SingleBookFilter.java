package com.betterreads.catalog.service.source;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides whether a search hit is a single book rather than a collection or a derivative work.
 *
 * <p>A discovery search returns single novels alongside boxed sets, multi-volume omnibuses, books
 * split into part editions, two-work bind-ups, adaptations, and study aids. Only single original
 * works belong in the catalog, so the rest are filtered out before staging. A numbered series volume
 * like {@code (Book 7)} is a single book and is kept; a multi-volume range like {@code Books 1-4} is
 * rejected.
 *
 * <p>The bind-up marker is the {@code /} separator only. A conjunction in the title ("Pride and
 * Prejudice", "Crime and Punishment") is part of one work, so {@code and} and {@code &} are not
 * treated as bind-up markers. The study-aid markers are the publisher brands and the
 * {@code Study Guide} / {@code Summary of} / {@code Analysis of} phrasings, which leaves a real title
 * such as "Notes from Underground" untouched.
 */
public final class SingleBookFilter {

    private static final List<Pattern> COLLECTION_MARKERS = List.of(
        Pattern.compile("box(?:ed)?\\s+set", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:books|volumes)\\s+\\d+\\s*-\\s*\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("complete\\b.*\\bset", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\(part\\s+\\d+/\\d+\\)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpart\\s+(?:one|two|three|four|five|\\d+)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\s/\\s", Pattern.CASE_INSENSITIVE),
        Pattern.compile("[\\[(]adaptation[\\])]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bspark\\s*notes\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcliffs\\s*notes\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bstudy\\s+guide\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:summary|analysis)\\s+of\\b", Pattern.CASE_INSENSITIVE));

    private SingleBookFilter() {
    }

    /** Returns true when the title is a single original work, false for a collection or derivative. */
    public static boolean isSingleBook(final String title) {
        return COLLECTION_MARKERS.stream().noneMatch(marker -> marker.matcher(title).find());
    }
}
