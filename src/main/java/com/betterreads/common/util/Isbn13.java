package com.betterreads.common.util;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

public final class Isbn13 {

    public static final Pattern PATTERN = Pattern.compile("97[89]\\d{10}");

    private Isbn13() {
    }

    public static boolean matches(final @Nullable String value) {
        return value != null && PATTERN.matcher(value).matches();
    }
}
