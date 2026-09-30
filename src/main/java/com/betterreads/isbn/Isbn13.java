package com.betterreads.isbn;

import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

public final class Isbn13 {

    public static final Pattern PATTERN = Pattern.compile("97[89]\\d{10}");

    private static final int CHECK_MODULUS = 10;

    private static final int ODD_WEIGHT = 3;

    private static final String ISBN10_PREFIX = "978";

    private static final int ISBN10_MODULUS = 11;

    private static final int ISBN10_X = 10;

    private Isbn13() {
    }

    public static @Nullable String toIsbn10(final @Nullable String isbn13) {
        if (isbn13 == null || !matches(isbn13) || !isbn13.startsWith(ISBN10_PREFIX)) {
            return null;
        }
        final String body = isbn13.substring(ISBN10_PREFIX.length(), isbn13.length() - 1);
        final int sum = IntStream.range(0, body.length())
            .map(index -> (body.length() + 1 - index) * Character.digit(body.charAt(index), CHECK_MODULUS))
            .sum();
        final int check = (ISBN10_MODULUS - sum % ISBN10_MODULUS) % ISBN10_MODULUS;
        return body + (check == ISBN10_X ? "X" : String.valueOf(check));
    }

    public static boolean matches(final @Nullable String value) {
        return value != null && PATTERN.matcher(value).matches();
    }

    public static boolean isValid(final @Nullable String value) {
        if (value == null || !matches(value)) {
            return false;
        }
        final int sum = IntStream.range(0, value.length() - 1)
            .map(index -> Character.digit(value.charAt(index), CHECK_MODULUS) * (index % 2 == 0 ? 1 : ODD_WEIGHT))
            .sum();
        final int check = (CHECK_MODULUS - sum % CHECK_MODULUS) % CHECK_MODULUS;
        return Character.digit(value.charAt(value.length() - 1), CHECK_MODULUS) == check;
    }
}
