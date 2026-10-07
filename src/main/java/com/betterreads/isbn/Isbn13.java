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

    private static final int ISBN10_LENGTH = 10;

    private static final Pattern ISBN10 = Pattern.compile("\\d{9}[\\dX]");

    private Isbn13() {
    }

    public static @Nullable String toIsbn10(final @Nullable String isbn13) {
        if (isbn13 == null || !matches(isbn13) || !isbn13.startsWith(ISBN10_PREFIX)) {
            return null;
        }
        final String body = isbn13.substring(ISBN10_PREFIX.length(), isbn13.length() - 1);
        final int check = (ISBN10_MODULUS - isbn10Sum(body) % ISBN10_MODULUS) % ISBN10_MODULUS;
        return body + (check == ISBN10_X ? "X" : String.valueOf(check));
    }

    public static @Nullable String fromIsbn10(final @Nullable String isbn10) {
        if (isbn10 == null || !ISBN10.matcher(isbn10).matches() || isbn10Sum(isbn10) % ISBN10_MODULUS != 0) {
            return null;
        }
        final String body = ISBN10_PREFIX + isbn10.substring(0, isbn10.length() - 1);
        return body + checkDigit(body);
    }

    public static boolean matches(final @Nullable String value) {
        return value != null && PATTERN.matcher(value).matches();
    }

    public static boolean isValid(final @Nullable String value) {
        if (value == null || !matches(value)) {
            return false;
        }
        return Character.digit(value.charAt(value.length() - 1), CHECK_MODULUS)
            == checkDigit(value.substring(0, value.length() - 1));
    }

    private static int checkDigit(final String body) {
        final int sum = IntStream.range(0, body.length())
            .map(index -> Character.digit(body.charAt(index), CHECK_MODULUS) * (index % 2 == 0 ? 1 : ODD_WEIGHT))
            .sum();
        return (CHECK_MODULUS - sum % CHECK_MODULUS) % CHECK_MODULUS;
    }

    private static int isbn10Sum(final String digits) {
        return IntStream.range(0, digits.length())
            .map(index -> (ISBN10_LENGTH - index) * isbn10Digit(digits.charAt(index)))
            .sum();
    }

    private static int isbn10Digit(final char digit) {
        return digit == 'X' ? ISBN10_X : Character.digit(digit, CHECK_MODULUS);
    }
}
