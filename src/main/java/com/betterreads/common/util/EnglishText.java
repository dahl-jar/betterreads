package com.betterreads.common.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Judges whether text is English prose by its share of common English function words.
 *
 * <p>Catalog sources sell foreign-language editions under English titles, so descriptions are
 * checked for English before scoring. The anchor branch carries its own minimum share, so a
 * foreign text with one English trailer sentence cannot pass on anchors alone.
 */
public final class EnglishText {

    private static final Set<String> ANCHOR_WORDS = Set.of("the", "of", "and", "to");

    private static final Set<String> FUNCTION_WORDS = Stream.concat(
        ANCHOR_WORDS.stream(),
        Stream.of(
            "a", "an", "in", "is", "that", "it", "with", "as", "for",
            "was", "on", "are", "be", "by", "this", "have", "from", "or", "his", "her", "they",
            "at", "but", "not", "what", "all", "were", "when", "who", "will", "would", "there",
            "their", "she", "he", "him", "has", "had", "its", "into", "than", "then", "them",
            "these", "so", "no", "out", "up", "one", "about", "after", "over", "only", "new",
            "more", "where", "most", "must", "can", "could", "now", "through", "while", "against"))
        .collect(Collectors.toUnmodifiableSet());

    private static final Pattern WORD = Pattern.compile("[a-z']+");

    private static final double MIN_FUNCTION_WORD_SHARE = 0.20;

    private static final double MIN_SHARE_WITH_ANCHORS = 0.12;

    private static final int MIN_DISTINCT_ANCHORS = 2;

    private EnglishText() {
    }

    /** Returns true when the text reads as English prose, false for another language or no words. */
    public static boolean isEnglish(final String text) {
        final Matcher words = WORD.matcher(text.toLowerCase(Locale.ROOT));
        int total = 0;
        int functionWords = 0;
        final Set<String> anchorsSeen = new HashSet<>();
        while (words.find()) {
            total++;
            final String word = words.group();
            if (FUNCTION_WORDS.contains(word)) {
                functionWords++;
            }
            if (ANCHOR_WORDS.contains(word)) {
                anchorsSeen.add(word);
            }
        }
        if (total == 0) {
            return false;
        }
        final double share = (double) functionWords / total;
        final boolean anchored =
            anchorsSeen.size() >= MIN_DISTINCT_ANCHORS && share >= MIN_SHARE_WITH_ANCHORS;
        return share >= MIN_FUNCTION_WORD_SHARE || anchored;
    }
}
