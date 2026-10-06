package com.betterreads.clients.websearch;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

final class PageText {

    private static final Map<Integer, String> FOLDS = Map.ofEntries(
        Map.entry(0x2018, "'"), Map.entry(0x2019, "'"), Map.entry(0x201A, "'"), Map.entry(0x201B, "'"),
        Map.entry(0x2032, "'"), Map.entry(0x201C, "\""), Map.entry(0x201D, "\""), Map.entry(0x201E, "\""),
        Map.entry(0x201F, "\""), Map.entry(0x2033, "\""), Map.entry(0x2010, "-"), Map.entry(0x2011, "-"),
        Map.entry(0x2012, "-"), Map.entry(0x2013, "-"), Map.entry(0x2014, "-"), Map.entry(0x2015, "-"),
        Map.entry(0x2212, "-"), Map.entry(0x00AD, ""), Map.entry(0x200B, ""), Map.entry(0x200C, ""),
        Map.entry(0x200D, ""), Map.entry(0x2060, ""), Map.entry(0xFEFF, ""));

    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private PageText() {
    }

    static String normalize(final String text) {
        return Folded.of(text).text();
    }

    static boolean holds(final String pageText, final String quote) {
        final String folded = normalize(quote);
        return !folded.isEmpty() && normalize(pageText).contains(folded);
    }

    static boolean hasWords(final String text, final String phrase) {
        final String words = words(phrase);
        return !words.isEmpty() && (" " + words(text) + " ").contains(" " + words + " ");
    }

    static int wordCount(final String text) {
        final String words = words(text);
        return words.isEmpty() ? 0 : words.split(" ").length;
    }

    static String without(final String text, final List<String> phrases) {
        String remaining = " " + words(text) + " ";
        for (final String phrase : phrases) {
            final String words = words(phrase);
            if (!words.isEmpty()) {
                remaining = remaining.replace(" " + words + " ", " ");
            }
        }
        return remaining.strip();
    }

    private static String words(final String text) {
        return String.join(" ", NOT_WORD.split(normalize(text))).strip();
    }

    static Optional<String> cut(final String pageText, final String start, final String end) {
        final String startKey = normalize(start);
        final String endKey = normalize(end);
        if (startKey.isEmpty() || endKey.isEmpty()) {
            return Optional.empty();
        }
        final Folded page = Folded.of(pageText);
        final int from = page.text().indexOf(startKey);
        if (from < 0) {
            return Optional.empty();
        }
        final int to = page.text().indexOf(endKey, from + startKey.length());
        if (to < 0) {
            return Optional.empty();
        }
        final int last = to + endKey.length() - 1;
        return Optional.of(pageText.substring(page.starts()[from], page.ends()[last]).strip());
    }

    private record Folded(String text, int[] starts, int[] ends) {

        static Folded of(final String original) {
            final Builder folded = new Builder(original.length());
            int index = 0;
            while (index < original.length()) {
                final int codePoint = original.codePointAt(index);
                final int next = index + Character.charCount(codePoint);
                if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                    folded.space();
                } else {
                    folded.append(fold(codePoint), index, next);
                }
                index = next;
            }
            return folded.build();
        }

        private static String fold(final int codePoint) {
            final String mapped = FOLDS.get(codePoint);
            if (mapped != null) {
                return mapped;
            }
            final String decomposed = Normalizer.normalize(Character.toString(codePoint), Normalizer.Form.NFKD);
            return decomposed.codePoints()
                .filter(part -> Character.getType(part) != Character.NON_SPACING_MARK)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .toLowerCase(Locale.ROOT);
        }
    }

    private static final class Builder {

        private char[] chars;

        private int[] starts;

        private int[] ends;

        private int length;

        private boolean pendingSpace;

        Builder(final int capacity) {
            this.chars = new char[capacity + 1];
            this.starts = new int[capacity + 1];
            this.ends = new int[capacity + 1];
        }

        void space() {
            pendingSpace = length > 0;
        }

        void append(final String text, final int start, final int end) {
            if (text.isEmpty()) {
                return;
            }
            if (pendingSpace) {
                put(' ', start, start);
                pendingSpace = false;
            }
            text.chars().forEach(character -> put((char) character, start, end));
        }

        private void put(final char character, final int start, final int end) {
            if (length == chars.length) {
                chars = Arrays.copyOf(chars, length * 2);
                starts = Arrays.copyOf(starts, length * 2);
                ends = Arrays.copyOf(ends, length * 2);
            }
            chars[length] = character;
            starts[length] = start;
            ends[length] = end;
            length++;
        }

        Folded build() {
            return new Folded(new String(chars, 0, length), starts, ends);
        }
    }
}
