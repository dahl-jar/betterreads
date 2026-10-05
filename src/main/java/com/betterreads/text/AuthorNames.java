package com.betterreads.text;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class AuthorNames {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern COMMA = Pattern.compile(",");

    private static final Pattern NAME_SUFFIX = Pattern.compile("(?i)(jr|sr|ii|iii|iv)\\.?");

    private static final Pattern MARKS = Pattern.compile("\\p{M}");

    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]");

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[,\\s]+$|(?<=\\p{L}{2})\\.\\s*$");

    private static final Pattern SEPARATORS = Pattern.compile("[;/]");

    private static final Pattern WORD_START = Pattern.compile("(^|[.\\-'])(\\p{L})");

    private static final Set<String> PARTICLES =
        Set.of("van", "von", "de", "der", "den", "del", "da", "di", "du", "la", "le", "ter");

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

    private static final String MC = "mc";

    private static final int LAST_FIRST_PARTS = 2;

    private AuthorNames() {
    }

    public static String key(final String name) {
        final String decomposed = Normalizer.normalize(firstLast(name.strip()), Normalizer.Form.NFKD);
        final String folded = MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).codePoints()
            .mapToObj(letter -> UNMARKED_LETTERS.getOrDefault(letter, Character.toString(letter)))
            .collect(Collectors.joining());
        return NOT_LETTER_OR_DIGIT.matcher(folded).replaceAll("");
    }

    public static String display(final String name) {
        final String trimmed = trimEnd(firstLast(trimEnd(collapseSpaces(name))));
        return isSingleCase(trimmed) ? titleCase(trimmed) : trimmed;
    }

    public static String collapseSpaces(final String name) {
        return WHITESPACE.matcher(name).replaceAll(" ").strip();
    }

    public static List<String> split(final String name) {
        return SEPARATORS.splitAsStream(name)
            .map(String::strip)
            .filter(part -> !part.isEmpty())
            .toList();
    }

    public static String surname(final String name) {
        final List<String> parts = lastFirstParts(name.strip());
        if (!parts.isEmpty()) {
            return parts.getFirst();
        }
        final List<String> words = WHITESPACE.splitAsStream(name.strip())
            .filter(word -> !isSuffix(word))
            .toList();
        return words.isEmpty() ? name.strip() : trimEnd(words.getLast());
    }

    public static boolean isSingleCase(final String name) {
        return name.equals(name.toUpperCase(Locale.ROOT)) || name.equals(name.toLowerCase(Locale.ROOT));
    }

    private static String firstLast(final String name) {
        final List<String> parts = lastFirstParts(name);
        return parts.isEmpty() ? name : parts.get(1) + " " + parts.getFirst();
    }

    private static List<String> lastFirstParts(final String name) {
        final List<String> parts = COMMA.splitAsStream(name).map(String::strip).toList();
        return parts.size() == LAST_FIRST_PARTS && !isSuffix(parts.get(1)) ? parts : List.of();
    }

    private static boolean isSuffix(final String part) {
        return NAME_SUFFIX.matcher(part).matches();
    }

    private static String trimEnd(final String name) {
        return TRAILING_PUNCTUATION.matcher(name).replaceAll("");
    }

    private static String titleCase(final String name) {
        final List<String> words = WHITESPACE.splitAsStream(name.toLowerCase(Locale.ROOT)).toList();
        return IntStream.range(0, words.size())
            .mapToObj(index -> index > 0 && PARTICLES.contains(words.get(index))
                ? words.get(index)
                : capitalize(words.get(index)))
            .collect(Collectors.joining(" "));
    }

    private static String capitalize(final String word) {
        final String capitalized = WORD_START.matcher(word)
            .replaceAll(match -> match.group(1) + match.group(2).toUpperCase(Locale.ROOT));
        return word.startsWith(MC) && word.length() > MC.length()
            ? "Mc" + capitalized.substring(MC.length(), MC.length() + 1).toUpperCase(Locale.ROOT)
                + capitalized.substring(MC.length() + 1)
            : capitalized;
    }
}
