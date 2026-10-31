package com.betterreads.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TextMatch {

    private static final Pattern SUBTITLE_TAIL = Pattern.compile("[:(].*$", Pattern.DOTALL);

    private static final Pattern PARENTHETICAL = Pattern.compile("\\([^)]*");

    private static final Pattern VOLUME_WORD =
        Pattern.compile("\\bvol(?:ume)?\\b", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern SEGMENT_BREAK = Pattern.compile("[:;,/#\\[\\]]|--|\\s[-\\u2013\\u2014]\\s");

    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(?:the|an|a)(?: |$)");

    private static final Pattern MARKS = Pattern.compile("\\p{M}");

    private static final Map<Integer, String> UNMARKED_LETTERS = Map.of(
        (int) 'œ', "oe",
        (int) 'æ', "ae",
        (int) 'ø', "o",
        (int) 'ı', "i",
        (int) 'ł', "l",
        (int) 'ß', "ss",
        (int) 'đ', "d",
        (int) 'ð', "d",
        (int) 'þ', "th");

    private TextMatch() {
    }

    static String fold(final String text) {
        final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
        return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).codePoints()
            .mapToObj(letter -> UNMARKED_LETTERS.getOrDefault(letter, Character.toString(letter)))
            .collect(Collectors.joining());
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
        final Set<String> firstSegments = segmentKeys(first);
        final Set<String> secondSegments = segmentKeys(second);
        return firstSegments.containsAll(secondSegments) || secondSegments.containsAll(firstSegments);
    }

    private static String wholeKey(final String title) {
        return wordKey(VOLUME_WORD.matcher(foldTitle(title)).replaceAll("vol"));
    }

    private static Set<String> segmentKeys(final String title) {
        final String bare = PARENTHETICAL.matcher(foldTitle(title)).replaceAll(" ");
        final String volumeSplit = VOLUME_WORD.matcher(bare).replaceAll(", vol");
        return SEGMENT_BREAK.splitAsStream(volumeSplit)
            .map(TextMatch::wordKey)
            .filter(key -> !key.isEmpty())
            .collect(Collectors.toSet());
    }

    private static String foldTitle(final String title) {
        return fold(title).replace("&", " and ");
    }

    private static String wordKey(final String text) {
        final String spaced = NOT_WORD.matcher(text).replaceAll(" ").strip();
        return LEADING_ARTICLE.matcher(spaced).replaceFirst("").replace(" ", "");
    }
}
