package com.betterreads.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TextMatch {

    private static final Pattern SUBTITLE_TAIL = Pattern.compile("[:(].*$", Pattern.DOTALL);

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static final Pattern PARENTHETICAL = Pattern.compile("\\([^)]*\\)?");

    private static final Pattern VOLUME_MARK =
        Pattern.compile("\\bvol(?:ume)?\\b", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern SEGMENT_BREAK = Pattern.compile("[:;,/#\\[\\]]|--|\\s[-\\u2013\\u2014]\\s");

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(?:the|an|a) ");

    private static final Set<String> BARE_ARTICLES = Set.of("the", "an", "a");

    private TextMatch() {
    }

    private static boolean containsIgnoreCase(final String haystack, final String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    public static boolean eitherContainsIgnoreCase(final String first, final String second) {
        return containsIgnoreCase(first, second) || containsIgnoreCase(second, first);
    }

    public static boolean titleWithinQuery(final String title, final String query) {
        final String trimmedTitle = title.trim();
        final String trimmedQuery = query.trim();
        return trimmedTitle.equalsIgnoreCase(trimmedQuery)
            || containsIgnoreCase(trimmedQuery, trimmedTitle);
    }

    public static boolean canonicalTitleMatches(final String first, final String second) {
        return coreTitle(first).equals(coreTitle(second));
    }

    public static String coreTitle(final String title) {
        return SUBTITLE_TAIL.matcher(title).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }

    public static boolean sameBookTitle(final String first, final String second) {
        if (wholeKey(first).equals(wholeKey(second))) {
            return true;
        }
        final Set<String> firstParts = segmentKeys(first);
        final Set<String> secondParts = segmentKeys(second);
        return firstParts.containsAll(secondParts) || secondParts.containsAll(firstParts);
    }

    private static String wholeKey(final String title) {
        return wordKey(VOLUME_MARK.matcher(fold(title)).replaceAll("vol "));
    }

    private static Set<String> segmentKeys(final String title) {
        final String bare = PARENTHETICAL.matcher(fold(title)).replaceAll(" ");
        final String marked = VOLUME_MARK.matcher(bare).replaceAll(", vol ");
        return SEGMENT_BREAK.splitAsStream(marked)
            .map(TextMatch::wordKey)
            .filter(key -> !key.isEmpty() && !BARE_ARTICLES.contains(key))
            .collect(Collectors.toSet());
    }

    private static String fold(final String title) {
        final String decomposed = Normalizer.normalize(title, Normalizer.Form.NFKD);
        final String unmarked = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return unmarked.toLowerCase(Locale.ROOT).replace("&", " and ");
    }

    private static String wordKey(final String text) {
        final String spaced = NON_WORD.matcher(text).replaceAll(" ").strip();
        return LEADING_ARTICLE.matcher(spaced).replaceFirst("").replace(" ", "");
    }
}
