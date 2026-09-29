package com.betterreads.isbn;

import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

public final class Isbn13 {

    public static final Pattern PATTERN = Pattern.compile("97[89]\\d{10}");

    private static final int CHECK_MODULUS = 10;

    private static final int ODD_WEIGHT = 3;

    private Isbn13() {
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
