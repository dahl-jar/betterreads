package com.betterreads.booksource;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tells single books apart from the boxed sets, omnibuses, part editions, bind-ups, adaptations
 * and study aids that search hits also return.
 *
 * <p>{@code (Book 7)} is one volume and stays, {@code Books 1-4} is a range and goes. "Pride and
 * Prejudice" is one work, so only {@code /} marks a bind-up. Study aids match on brand names and
 * {@code Study Guide}, {@code Summary of} or {@code Analysis of} so "Notes from Underground" stays.
 */
public final class SingleBookFilter {

    private static final List<Pattern> COLLECTION_MARKERS = List.of(
        Pattern.compile("box(?:ed)?\\s+set", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:books|volumes)\\s+\\d+\\s*-\\s*\\d+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("complete\\b.*\\bset", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpart\\s+(?:one|two|three|four|five|\\d+)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\s/\\s"),
        Pattern.compile("[\\[(]adaptation[\\])]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bspark\\s*notes\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcliffs\\s*notes\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bstudy\\s+guide\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:summary|analysis)\\s+of\\b", Pattern.CASE_INSENSITIVE));

    private SingleBookFilter() {
    }

    public static boolean isSingleBook(final String title) {
        return COLLECTION_MARKERS.stream().noneMatch(marker -> marker.matcher(title).find());
    }
}
