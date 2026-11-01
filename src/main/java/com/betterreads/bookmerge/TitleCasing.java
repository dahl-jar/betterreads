package com.betterreads.bookmerge;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

final class TitleCasing {

    private static final String ENGLISH = "en";

    private static final String SPACE = " ";

    private static final int MIN_WORDS = 2;

    private static final char PERIOD = '.';

    private static final Set<String> ROMAN_NUMERALS = Set.of(
        "ii", "iii", "iv", "vi", "vii", "viii", "ix", "xi", "xii", "xiii", "xiv", "xv", "xvi", "xvii",
        "xviii", "xix", "xx");

    private static final Pattern PUNCTUATION = Pattern.compile("\\p{Punct}");

    private static final Pattern PRONOUN_I = Pattern.compile("(?<=\\s)I\\b");

    private static final Set<String> SMALL_WORDS = Set.of(
        "a", "an", "the", "and", "but", "or", "nor", "for", "so", "yet",
        "as", "at", "by", "in", "of", "on", "to", "up", "via", "with");

    private static final Set<String> PHRASE_ENDINGS = Set.of(":", "?", "!");

    private static final Set<String> STANDALONE_DASHES = Set.of("-", "--", "\u2013", "\u2014");

    private TitleCasing() {
    }

    static String capitalize(
        final String winner, final List<String> alternatives, final @Nullable String language) {
        return betterCasedAlternative(winner, alternatives)
            .orElseGet(() -> ENGLISH.equals(language) && !isCased(winner) ? titleCase(winner) : winner);
    }

    static boolean isAllCaps(final String title) {
        return words(title).filter(word -> !word.isEmpty()).count() >= MIN_WORDS
            && title.codePoints().anyMatch(Character::isUpperCase)
            && title.codePoints().noneMatch(Character::isLowerCase);
    }

    static String fixAllCaps(final String title, final @Nullable String language) {
        if (!ENGLISH.equals(language) || !isAllCaps(title)) {
            return title;
        }
        return titleCase(words(title)
            .map(word -> keepsCapitals(word) ? word : word.toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(SPACE)));
    }

    private static Stream<String> words(final String title) {
        return Arrays.stream(title.split(SPACE));
    }

    private static boolean keepsCapitals(final String word) {
        final int period = word.indexOf(PERIOD);
        final boolean initials = period >= 0 && period + 1 < word.length()
            && Character.isLetter(word.codePointAt(period + 1));
        return initials || ROMAN_NUMERALS.contains(bareWord(word));
    }

    private static String bareWord(final String word) {
        return PUNCTUATION.matcher(word).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static Optional<String> betterCasedAlternative(final String winner, final List<String> alternatives) {
        final long winnerCount = capitalizedWordCount(winner);
        return alternatives.stream()
            .filter(alternative -> !isAllCaps(alternative))
            .filter(alternative -> alternative.equalsIgnoreCase(winner))
            .filter(alternative -> capitalizedWordCount(alternative) > winnerCount)
            .max(Comparator.comparingLong(TitleCasing::capitalizedWordCount));
    }

    private static long capitalizedWordCount(final String title) {
        return words(title)
            .filter(word -> !word.isEmpty() && Character.isUpperCase(word.codePointAt(0)))
            .count();
    }

    private static boolean isCased(final String title) {
        return PRONOUN_I.matcher(title).replaceAll("").codePoints().skip(1).anyMatch(Character::isUpperCase);
    }

    private static String titleCase(final String title) {
        final String[] words = title.split(SPACE);
        final StringJoiner cased = new StringJoiner(SPACE);
        String lastWord = "";
        for (int index = 0; index < words.length; index++) {
            final String word = words[index];
            final boolean firstOrLast = index == 0 || index == words.length - 1;
            cased.add(firstOrLast || endsPhrase(lastWord) || !isSmallWord(word) ? capitalizeWord(word) : word);
            lastWord = word.isEmpty() ? lastWord : word;
        }
        return cased.toString();
    }

    private static boolean endsPhrase(final String word) {
        return STANDALONE_DASHES.contains(word) || PHRASE_ENDINGS.stream().anyMatch(word::endsWith);
    }

    private static boolean isSmallWord(final String word) {
        return SMALL_WORDS.contains(bareWord(word));
    }

    private static String capitalizeWord(final String word) {
        final int start = IntStream.range(0, word.length())
            .filter(index -> Character.isLetterOrDigit(word.codePointAt(index)))
            .findFirst()
            .orElse(word.length());
        if (start == word.length()) {
            return word;
        }
        final int first = word.codePointAt(start);
        return word.substring(0, start)
            + Character.toString(Character.toUpperCase(first))
            + word.substring(start + Character.charCount(first));
    }
}
