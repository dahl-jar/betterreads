package com.betterreads.catalog.service.source.quality;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

public final class TitleCasing {

    private static final String ENGLISH = "en";

    private static final String SPACE = " ";

    private static final Pattern PUNCTUATION = Pattern.compile("\\p{Punct}");

    private static final Set<String> SMALL_WORDS = Set.of(
        "a", "an", "the", "and", "but", "or", "nor", "for", "so", "yet",
        "as", "at", "by", "in", "of", "on", "to", "up", "via", "with");

    private TitleCasing() {
    }

    public static String capitalize(
        final String winner, final List<String> alternatives, final @Nullable String language) {
        return betterCasedAlternative(winner, alternatives)
            .orElseGet(() -> ENGLISH.equals(language) && !hasCapitalAfterFirst(winner) ? titleCase(winner) : winner);
    }

    private static Optional<String> betterCasedAlternative(final String winner, final List<String> alternatives) {
        final long winnerCount = capitalizedWordCount(winner);
        return alternatives.stream()
            .filter(alternative -> alternative.equalsIgnoreCase(winner))
            .filter(alternative -> capitalizedWordCount(alternative) > winnerCount)
            .max(Comparator.comparingLong(TitleCasing::capitalizedWordCount));
    }

    private static long capitalizedWordCount(final String title) {
        return Arrays.stream(title.split(SPACE))
            .filter(word -> !word.isEmpty() && Character.isUpperCase(word.codePointAt(0)))
            .count();
    }

    private static boolean hasCapitalAfterFirst(final String title) {
        return title.codePoints().skip(1).anyMatch(Character::isUpperCase);
    }

    private static String titleCase(final String title) {
        final String[] words = title.split(SPACE);
        final int last = words.length - 1;
        return IntStream.rangeClosed(0, last)
            .mapToObj(index -> index == 0 || index == last || !isSmallWord(words[index])
                ? capitalizeWord(words[index])
                : words[index])
            .collect(Collectors.joining(SPACE));
    }

    private static boolean isSmallWord(final String word) {
        return SMALL_WORDS.contains(PUNCTUATION.matcher(word).replaceAll("").toLowerCase(Locale.ROOT));
    }

    private static String capitalizeWord(final String word) {
        if (word.isEmpty()) {
            return word;
        }
        final int first = word.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(first)))
            + word.substring(Character.charCount(first));
    }
}
